# 監視とログ

AWS で動かしているあいだ、止まったこととエラーが増えたことを CloudWatch で知らせる。無料枠の範囲で使う（ログ 5GB、アラーム 10個、カスタム指標 10個）。

## 構成

- アプリはログを標準出力に出すだけで、CloudWatch を知らない
- Docker の `awslogs` ドライバーが、ログを CloudWatch Logs の `/sns-app/app` に送る。保存は7日
- ログの中身から、メトリクスフィルターが指標を作る
- メモリとディスクは、EC2 の `report-metrics` が5分ごとに送る。常駐の Agent は置かない（EC2 のメモリは1GB）
- アラームは SNS のトピック `sns-app-alarms` からメールで通知する
- トピックと宛先だけは `infra/terraform/storage` にあり、`down.ps1` で消えない。ほかは環境と一緒に消えるので、止めているあいだは監視も通知もしない

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

`Heartbeat` は、DB につながることを確かめて、1分ごとに INFO のログを出す。

## アラーム

`infra/terraform/monitoring.tf` にある。どれも初期値で、動かしながら調整する。

| アラーム | 条件 |
| --- | --- |
| アプリが応答しない | `Heartbeat` のログが届かない。止めてから通知まで約8分（2026-11-07 に実測） |
| サーバーエラーが多い | 5分の `ERROR` が5件を超える |
| 応答が遅い | 10分の「遅いリクエスト」が20件を超える |
| 確認メールを送れない | 5分に1件でも失敗する |
| メモリが足りない | 空きが10分続けて10%を下回る |
| ディスクが足りない | 使用率が10分続けて85%を超える |

「アプリが応答しない」は、データ不足に変わったときに通知する。EC2 ごと止まったときも通知される。`up.ps1` でアプリが起動すると OK に変わるので、OK のメールが1通届く。

アラームの名前はメールの件名に出る。日本語は件名で崩れるので英字にして、説明を本文に出す。

## 通知先の登録

1. `infra/terraform/storage/terraform.tfvars` に `alarm_email = "メールアドレス"` と書く（Git には入らない）
2. `up.ps1` を実行する
3. AWS から届くメールの「Confirm subscription」を開く。最初の1回だけ

## アラームが来たら

1. CloudFront の URL を Chrome で開き、ログインと投稿ができるか確かめる
2. CloudWatch Logs Insights で `/sns-app/app` を開き、その時刻の `level = "ERROR"` のログを見る
3. `request_id` で、同じリクエストのログを絞る
4. 応答しないときは、Session Manager で EC2 に入り、`docker compose ps` と `docker compose logs app` を見る
5. 不具合なら、再現するテストを先に書いて直す

`down.ps1` でログも消えるので、調べ終わってから止める。
