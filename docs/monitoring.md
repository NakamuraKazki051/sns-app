# 監視とログ

AWS で動かしているあいだ、止まったこととエラーが増えたことを Datadog で知らせる。Datadog の API キーを登録するまでは、何も送らない。

## 構成

- アプリはログを標準出力に出すだけで、Datadog を知らない
- EC2 の Datadog Agent が、コンテナのログとサーバーの指標を Datadog に送る
- API キーは Parameter Store の `/sns-app/datadog/api-key` に置く。無ければ Agent は起動しない
- Agent のメモリは 256MB までにする（EC2 は1GB、スワップ2GB）

## ログ

AWS では `LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash` で、1行1件の JSON にする。ローカルは今まで通りの文章の形。

`/api/` のリクエストごとに `RequestLogFilter` が1件出す。1秒以上かかったものは `WARN` の「遅いリクエスト」にする。応答の `X-Request-Id` と、ログの `request_id` は同じ値。

| 項目 | 内容 |
| --- | --- |
| `request_id` | リクエストの番号。処理中のログすべてに付く |
| `user_id` | ログイン中のユーザーの ID |
| `method`、`path` | クエリ文字列は出さない |
| `status` | 応答のコード |
| `duration_ms` | 処理にかかった時間 |

出さないもの: パスワード、Cookie、秘密のヘッダー、メールアドレス、確認リンクのトークン、投稿の本文。例外は、メールを送らないローカルで確認リンクを出す処理だけ。

EC2 の中のログは 10MB × 3 世代まで。`down.ps1` で EC2 と一緒に消える。

## アラート

`infra/datadog/monitors.tf` にある。どれも初期値で、動かしながら調整する。

| アラート | 条件 |
| --- | --- |
| アプリが応答しない | 8080番に3回続けてつながらない |
| サーバーエラーが多い | 5分の `ERROR` が5件を超える |
| 応答が遅い | 10分の「遅いリクエスト」が20件を超える |
| 確認メールを送れない | 5分に1件でも失敗する |
| メモリが足りない | 空きが10分の平均で10%を下回る |
| ディスクが足りない | 使用率が85%を超える |

データが届かないだけではアラートを出さない。`down.ps1` のたびに通知が来るのを防ぐため。そのため、EC2 ごと止まった場合は通知されない。

## 有効にする手順

1. Datadog のアカウントを、サイト `ap1.datadoghq.com`（日本）で作る。料金は登録前に確かめる
2. `./infra/datadog/set-api-key.ps1` で API キーを登録する。キーはチャットに貼らない
3. `infra/terraform` の次の `up.ps1` から、Agent が動く
4. `infra/datadog` で、`terraform.tfvars` に通知先を書く
5. 環境変数 `DD_API_KEY` と `DD_APP_KEY` を設定して、`terraform apply` でアラートを作る

ログを送らないときは、`infra/terraform` の `datadog_logs_enabled` を `false` にする。

## アラートが来たら

1. CloudFront の URL を Chrome で開き、ログインと投稿ができるか確かめる
2. Datadog で、その時刻の `ERROR` のログを開く
3. `request_id` で、同じリクエストのログを絞る
4. 応答しないときは、Session Manager で EC2 に入り、`docker compose ps` と `docker compose logs app` を見る
5. 不具合なら、再現するテストを先に書いて直す
