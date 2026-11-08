# AWS 環境（Terraform）

AWS の無料枠の範囲で、RaiseLine を動かす EC2 を1台と、RDS（MySQL）を1台作ります。手本は `task-manager-app` の `infra/terraform` です。

## 作られるもの

| リソース | 内容 |
|---|---|
| EC2 | `t3.micro`（無料枠の対象）、Amazon Linux 2023、ディスク20GB（gp3・暗号化） |
| RDS | MySQL 8.4、`db.t4g.micro`（無料枠の対象）、シングルAZ、ストレージ20GB（暗号化）、自動バックアップ1日分 |
| CloudFront | ブラウザとの間を HTTPS にする。証明書は CloudFront の標準のもの（`xxxx.cloudfront.net`）で、独自ドメインは要らない。キャッシュはしない |
| Elastic IP | 固定のIPアドレス。サーバーを止めて起動し直してもIPが変わらない |
| セキュリティグループ（EC2） | 80番を **CloudFront からだけ** 開ける（AWS 管理のプレフィックスリスト）。`http://<IP>/` は直接開けない。SSH（22番）は開けない。これは CloudFront 全体に開くので、自分の CloudFront かどうかはアプリが秘密のヘッダーで確かめる |
| セキュリティグループ（RDS） | 3306番を **アプリのEC2からだけ** 許可する。RDSはインターネットから接続できない（`publicly_accessible = false`） |
| Parameter Store | DBのパスワード（SecureString。標準パラメータなので無料） |
| S3バケット | デプロイするファイル（`app.jar`）の受け渡し場所。非公開・暗号化。環境と一緒に消える |
| S3バケット（画像） | 投稿の画像（`post-images/`）。非公開・暗号化・https のみ。**`storage/` の別の Terraform で作り、`down.ps1` では消さない**（投稿は DB のスナップショットで残るので、画像も残す）。`up.ps1` が毎回 apply する（あれば何もしない）。消すには `prevent_destroy` を外してから `storage/` で destroy する |
| IAMロール | Session Manager の権限、DBのパスワードのパラメータ1つを読む権限、デプロイ用のS3バケットのファイルを読む権限、画像のバケットの `post-images/` の中だけを読み書き・削除する権限 |

## 構成

```
ブラウザ ──HTTPS──▶ CloudFront ──HTTP 80──▶ Spring Boot（コンテナの中は 8080、Java 25）──3306──▶ RDS（MySQL）
                                            ├─ /api/… → API
                                            └─ それ以外 → 画面（React をビルドして jar に同梱したもの）
```

画面も API も Spring Boot 1つが返すので、task-manager-app と違って nginx は置きません。画面と API が同じオリジンなので、ログインの Cookie がそのまま使えます。

HTTPS で守られるのは **ブラウザ〜CloudFront** の間です。CloudFront〜EC2 の間は HTTP のままなので、次の設定が要ります（どちらも `user_data.sh.tftpl`）。

- `SESSION_COOKIE_SECURE=true`：ログインの Cookie に `Secure` を付ける
- `APP_CORS_ALLOWED_ORIGINS=https://<CloudFront のドメイン>`：アプリには `http` で届くので、ブラウザの `Origin: https://…` が別のサイトに見える。これが無いと POST が 403 になる
- `JWT_SECRET`：ログインの JWT に署名する鍵（64文字）。Terraform の `random_password` が作り、環境を作り直すたびに変わる（そのときは全員がログインし直す）

- `APP_ORIGIN_SECRET`：CloudFront が付ける秘密のヘッダー（`X-Origin-Verify`）の値。無いリクエストは 403。他の人の CloudFront からは届かない。環境を作り直すたびに変わる
- `REGISTER_PER_HOUR=5`：1つの IP から1時間に登録できる回数
- `APP_PUBLIC_URL`：確認メールのリンクの先（CloudFront の URL）
- `MAIL_USERNAME`・`MAIL_PASSWORD`：確認メールを送る Gmail（下の「確認メールの送信」）

API 仕様書（Swagger UI）はローカルだけで使うので、AWS では `API_DOCS_ENABLED=false` で切っています（`/swagger-ui.html` と `/v3/api-docs` は 404）。

テーブルは、アプリの起動時に Flyway が作ります（`backend/src/main/resources/db/migration`）。空の DB でも、スナップショットから戻した DB でも、まだ流していない SQL だけを流します。

DBのパスワードは、`terraform apply` の間だけ作られ、RDSと Parameter Store に直接渡されます（ephemeral + write-only）。Terraform の state にもGitにも残りません。

サーバーの初回起動時に `user_data.sh.tftpl` が次の準備をします。

- 2GBのスワップを作る（メモリ1GBで Spring Boot を動かすため）
- Docker と Docker Compose を入れ、Java 25 のイメージを取っておく
- Parameter Store からDBのパスワードを読み、`/opt/sns-app/.env`（rootだけが読める）に保存する
- `/opt/sns-app/docker-compose.yml` を置く（Docker Compose の版は固定している）
- 終わったら `/opt/sns-app/.ready` を作る（`deploy.ps1` はこれを待つ）

## 無料枠に収めるための工夫と、注意点

- NATゲートウェイ・ロードバランサー・Secrets Manager など、有料になりやすい部品は使いません
- RDSは無料枠の対象のサイズにし、Performance Insights と拡張モニタリングは使いません
- **使うときだけ作り、使い終わったら消す運用です**（下の「使い方」）。消している間にかかるのは、DBのスナップショットと、投稿の画像（S3）の保存料金だけです（ほぼ0円）
- `terraform destroy` を直接実行すると、スナップショットを取らずにRDSのデータが消えます。消すときは必ず `down.ps1` を使ってください
- CPUクレジットを `standard` にしています。CPUを使い切っても遅くなるだけで、追加料金はかかりません
- **パブリックIPv4アドレス（Elastic IP）は無料枠の対象外です**（月に約3.6ドル）。無料プランのクレジットから引かれます
- CloudFront は転送量が月1TBまで無料です。画面も API もキャッシュせず、そのままアプリへ渡します
- CloudFront〜EC2 の間は暗号化されません。一般公開するなら、独自ドメインを取って EC2 まで HTTPS にします
- task-manager-app・finance-manager-app の環境と同時に動かすと、無料枠（EC2・RDS それぞれ月750時間）を分け合うことになります。使わない環境は消しておいてください

## 使い方

前提：`aws login` でログインしていること。PowerShell 7（`pwsh`）で実行します。Terraform は `~/tools/terraform/terraform.exe` にあります。

```powershell
cd infra/terraform
./up.ps1       # 使うとき：環境を作り、アプリもデプロイする（20分ほど）。終わるとURLが表示される
./deploy.ps1   # コードを変えたとき：ビルドして、今ある環境にデプロイし直す（3〜5分）
./down.ps1     # 使い終わったら：DBをスナップショットに保存してから、環境を全部消す（10分ほど）
```

| スクリプト | やること |
|---|---|
| `up.ps1` | 画像のバケットが無ければ作る（`storage/`）。最新のDBスナップショットがあれば、そこから RDS を復元して環境を作る（なければ空のDB）。そのあと `deploy.ps1` を実行してURLを表示する。`-SkipDeploy` を付けると作るだけ |
| `deploy.ps1` | 画面（`npm run build`）と `app.jar`（`mvn package`）をビルドし、非公開のS3バケットに置く。EC2 が Session Manager 経由でそれを取り込み、アプリを起動し直す。ログインなしの `/api/auth/me` が 401 を返したら完了。`-SkipBuild` を付けるとビルドを省く |
| `down.ps1` | RDSのスナップショットを取り、完成を待ってから `terraform destroy` で全部消す（CloudFront も消える）。スナップショットに失敗したら何も消さずに止まる。スナップショットは新しいものから2つだけ残す |

- **作り直すたびに URL（`xxxx.cloudfront.net`）が変わります。** `up.ps1` の最後に表示されるURLを使ってください
- EC2 のサイズなどを変えたい場合は、`terraform.tfvars.example` を `terraform.tfvars` という名前でコピーして編集します

## 確認メールの送信

新規登録の確認メールは Gmail から送ります。最初に1回だけ、次の準備が要ります。

1. Google アカウントで2段階認証を有効にし、アプリパスワードを作ります。
2. Gmail のアドレスとアプリパスワードを Parameter Store に入れます（公開リポジトリと Terraform の state に載せないためです）。`down.ps1` では消えません。

```powershell
aws ssm put-parameter --region ap-northeast-1 --type SecureString --name /sns-app/mail/username --value 'Gmail のアドレス'
aws ssm put-parameter --region ap-northeast-1 --type SecureString --name /sns-app/mail/password --value 'アプリパスワード'
```

- 値はサーバーを作るときに読みます。動いている環境に入れた場合は、`down.ps1` と `up.ps1` で作り直してください。
- 未登録のままだと、メールは送られず、リンクはアプリのログに出るだけです（`up.ps1` が警告します）。
- Gmail の送信数には1日の上限があります。受信者には送信元の Gmail アドレスが見えます。

## サーバーに入る

SSHの鍵は使いません。`& $tf output connect_command` で表示されるコマンドを実行します（PC に Session Manager プラグインが必要）。EC2コンソールの「接続」→「セッションマネージャー」からも入れます。

```powershell
aws ssm start-session --target <インスタンスID> --region ap-northeast-1
```

アプリのログは、サーバーの中で `cd /opt/sns-app && sudo docker compose logs --tail 100 app` で見られます。

## 品質チェック

```powershell
$tf = "$env:USERPROFILE\tools\terraform\terraform.exe"
$r = 'ap-northeast-1'

& $tf fmt -check -recursive   # 書式（何も表示されなければ OK）
& $tf validate                # 「Success! The configuration is valid.」なら OK
& $tf plan                    # 環境があるとき。「No changes.」なら、コンソールで手で変えたものはない

# RDS がインターネットに公開されていないこと（False なら OK）
aws rds describe-db-instances --region $r --db-instance-identifier sns-app-db --query "DBInstances[0].PubliclyAccessible"

# state にDBのパスワードが入っていないこと（0 なら OK）
$p = aws ssm get-parameter --region $r --name /sns-app/db/password --with-decryption --query Parameter.Value --output text
(Select-String -Path terraform.tfstate -Pattern $p -SimpleMatch).Count
Remove-Variable p

# 外から 80・8080 と RDS に入れないこと（どれも False なら OK）
$ip = & $tf output -raw public_ip
$c = [Net.Sockets.TcpClient]::new(); $c.ConnectAsync($ip, 80).Wait(5000); $c.Dispose()
$c = [Net.Sockets.TcpClient]::new(); $c.ConnectAsync($ip, 8080).Wait(5000); $c.Dispose()
$c = [Net.Sockets.TcpClient]::new(); $c.ConnectAsync((& $tf output -raw db_endpoint), 3306).Wait(5000); $c.Dispose()
```

`down.ps1` のあとの消し忘れ確認（どれも空なら OK。スナップショットは2つまで）:

```powershell
aws ec2 describe-instances --region $r --filters Name=tag:Project,Values=sns-app Name=instance-state-name,Values=pending,running,stopping,stopped --query "Reservations[].Instances[].InstanceId" --output text
aws ec2 describe-addresses --region $r --query "Addresses[].PublicIp" --output text
aws rds describe-db-instances --region $r --query "DBInstances[].DBInstanceIdentifier" --output text
aws cloudfront list-distributions --query "DistributionList.Items[?Comment=='sns-app'].Id" --output text
aws rds describe-db-snapshots --region $r --snapshot-type manual --query "DBSnapshots[?starts_with(DBSnapshotIdentifier, 'sns-app-db-')].DBSnapshotIdentifier" --output text
```

## state について

`terraform.tfstate` と `storage/terraform.tfstate` には、作ったリソースの情報が入っています。このPCにだけ保存していて、Gitには入れません（`.gitignore` 済み）。消すと Terraform がリソースを管理できなくなるので、消さないでください。`terraform.tfstate` には、秘密のヘッダーの値も入っています（CloudFront の設定に必要なため）。DB のパスワードは入っていません。特に `storage/` の state は `down.ps1` のあとも画像のバケットを指しています。消すと、次の `up.ps1` がバケットを作ろうとして失敗します。
