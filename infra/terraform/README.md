# AWS 環境（Terraform）

AWS の無料枠の範囲で、RaiseLine を動かす EC2 を1台と、RDS（MySQL）を1台作ります。手本は `task-manager-app` の `infra/terraform` です。

## 作られるもの

| リソース | 内容 |
|---|---|
| EC2 | `t3.micro`（無料枠の対象）、Amazon Linux 2023、ディスク20GB（gp3・暗号化） |
| RDS | MySQL 8.4、`db.t4g.micro`（無料枠の対象）、シングルAZ、ストレージ20GB（暗号化）、自動バックアップ1日分 |
| Elastic IP | 固定のIPアドレス。サーバーを止めて起動し直してもURLが変わらない |
| セキュリティグループ（EC2） | 80番だけ開ける。SSH（22番）は開けない |
| セキュリティグループ（RDS） | 3306番を **アプリのEC2からだけ** 許可する。RDSはインターネットから接続できない（`publicly_accessible = false`） |
| Parameter Store | DBのパスワード（SecureString。標準パラメータなので無料） |
| S3バケット | デプロイするファイル（`app.jar`）の受け渡し場所。非公開・暗号化。環境と一緒に消える |
| IAMロール | Session Manager の権限、DBのパスワードのパラメータ1つを読む権限、S3バケットのファイルを読む権限だけ |

## 構成

```
ブラウザ ──80──▶ Spring Boot（コンテナの中は 8080、Java 25）──3306──▶ RDS（MySQL）
                  ├─ /api/… → API
                  └─ それ以外 → 画面（React をビルドして jar に同梱したもの）
```

画面も API も Spring Boot 1つが返すので、task-manager-app と違って nginx は置きません。画面と API が同じオリジン（`http://<IP>`）なので、ログインの Cookie がそのまま使え、CORS の設定も要りません。

テーブルは、アプリの起動時に Flyway が作ります（`backend/src/main/resources/db/migration`）。空の DB でも、スナップショットから戻した DB でも、まだ流していない SQL だけを流します。

DBのパスワードは、`terraform apply` の間だけ作られ、RDSと Parameter Store に直接渡されます（ephemeral + write-only）。Terraform の state にもGitにも残りません。

サーバーの初回起動時に `user_data.sh.tftpl` が次の準備をします。

- 2GBのスワップを作る（メモリ1GBで Spring Boot を動かすため）
- Docker と Docker Compose を入れ、Java 25 のイメージを取っておく
- Parameter Store からDBのパスワードを読み、`/opt/sns-app/.env`（rootだけが読める）に保存する
- `/opt/sns-app/docker-compose.yml` を置く
- 終わったら `/opt/sns-app/.ready` を作る（`deploy.ps1` はこれを待つ）

## 無料枠に収めるための工夫と、注意点

- NATゲートウェイ・ロードバランサー・Secrets Manager など、有料になりやすい部品は使いません
- RDSは無料枠の対象のサイズにし、Performance Insights と拡張モニタリングは使いません
- **使うときだけ作り、使い終わったら消す運用です**（下の「使い方」）。消している間にかかるのは、DBのスナップショットの保存料金だけです（ほぼ0円）
- `terraform destroy` を直接実行すると、スナップショットを取らずにRDSのデータが消えます。消すときは必ず `down.ps1` を使ってください
- CPUクレジットを `standard` にしています。CPUを使い切っても遅くなるだけで、追加料金はかかりません
- **パブリックIPv4アドレス（Elastic IP）は無料枠の対象外です**（月に約3.6ドル）。無料プランのクレジットから引かれます
- 通信はHTTP（暗号化なし）です。HTTPS にするには独自ドメインが必要なので、必要になったら追加します（そのとき Cookie に `Secure` を付ける）
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
| `up.ps1` | 最新のDBスナップショットがあれば、そこから RDS を復元して環境を作る（なければ空のDB）。そのあと `deploy.ps1` を実行してURLを表示する。`-SkipDeploy` を付けると作るだけ |
| `deploy.ps1` | 画面（`npm run build`）と `app.jar`（`mvn package`）をビルドし、非公開のS3バケットに置く。EC2 が Session Manager 経由でそれを取り込み、アプリを起動し直す。ログインなしの `/api/auth/me` が 401 を返したら完了。`-SkipBuild` を付けるとビルドを省く |
| `down.ps1` | RDSのスナップショットを取り、完成を待ってから `terraform destroy` で全部消す。スナップショットに失敗したら何も消さずに止まる。スナップショットは新しいものから2つだけ残す |

- **作り直すたびにIPアドレス（URL）が変わります。** `up.ps1` の最後に表示されるURLを使ってください
- アクセスを自分の回線だけに絞りたい場合は、`terraform.tfvars.example` を `terraform.tfvars` という名前でコピーして編集します

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

# 外から 8080 と RDS に入れないこと（どちらも False なら OK）
$ip = & $tf output -raw public_ip
$c = [Net.Sockets.TcpClient]::new(); $c.ConnectAsync($ip, 8080).Wait(5000); $c.Dispose()
$c = [Net.Sockets.TcpClient]::new(); $c.ConnectAsync((& $tf output -raw db_endpoint), 3306).Wait(5000); $c.Dispose()
```

`down.ps1` のあとの消し忘れ確認（どれも空なら OK。スナップショットは2つまで）:

```powershell
aws ec2 describe-instances --region $r --filters Name=tag:Project,Values=sns-app Name=instance-state-name,Values=pending,running,stopping,stopped --query "Reservations[].Instances[].InstanceId" --output text
aws ec2 describe-addresses --region $r --query "Addresses[].PublicIp" --output text
aws rds describe-db-instances --region $r --query "DBInstances[].DBInstanceIdentifier" --output text
aws rds describe-db-snapshots --region $r --snapshot-type manual --query "DBSnapshots[?starts_with(DBSnapshotIdentifier, 'sns-app-db-')].DBSnapshotIdentifier" --output text
```

## state について

`terraform.tfstate` には、作ったリソースの情報が入っています。このPCにだけ保存していて、Gitには入れません（`.gitignore` 済み）。消すと Terraform がリソースを管理できなくなるので、消さないでください。
