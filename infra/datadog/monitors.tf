# 監視のアラート。条件の考え方は docs/monitoring.md
# EC2 は必要なときだけ動かすので、データが届かないだけではアラートを出さない（notify_no_data = false）

locals {
  tags = ["service:raiseline", "managed-by:terraform"]
}

resource "datadog_monitor" "app_down" {
  name    = "RaiseLine: アプリが応答しない"
  type    = "service check"
  query   = "\"tcp.can_connect\".over(\"instance:raiseline-app\").by(\"host\").last(4).count_by_status()"
  message = "8080番につながりません。コンテナの状態を確かめてください。 ${var.notify}"
  tags    = local.tags

  monitor_thresholds {
    critical = 3
    ok       = 1
  }
  notify_no_data = false
}

resource "datadog_monitor" "errors" {
  name    = "RaiseLine: サーバーエラーが多い"
  type    = "log alert"
  query   = "logs(\"service:raiseline status:error\").index(\"*\").rollup(\"count\").last(\"5m\") > 5"
  message = "5分の ERROR が5件を超えました。request_id で原因のログを追ってください。 ${var.notify}"
  tags    = local.tags

  monitor_thresholds {
    critical = 5
  }
  notify_no_data = false
}

resource "datadog_monitor" "slow" {
  name    = "RaiseLine: 応答が遅い"
  type    = "log alert"
  query   = "logs(\"service:raiseline \\\"遅いリクエスト\\\"\").index(\"*\").rollup(\"count\").last(\"10m\") > 20"
  message = "10分に1秒を超えたリクエストが20件を超えました。遅い path と RDS を見てください。 ${var.notify}"
  tags    = local.tags

  monitor_thresholds {
    critical = 20
  }
  notify_no_data = false
}

resource "datadog_monitor" "mail" {
  name    = "RaiseLine: 確認メールを送れない"
  type    = "log alert"
  query   = "logs(\"service:raiseline \\\"確認メールを送れませんでした\\\"\").index(\"*\").rollup(\"count\").last(\"5m\") > 0"
  message = "確認メールの送信に失敗しました。Gmail のアプリパスワードを確かめてください。 ${var.notify}"
  tags    = local.tags

  monitor_thresholds {
    critical = 0
  }
  notify_no_data = false
}

resource "datadog_monitor" "memory" {
  name    = "RaiseLine: メモリが足りない"
  type    = "metric alert"
  query   = "avg(last_10m):avg:system.mem.pct_usable{service:raiseline} by {host} < 0.1"
  message = "メモリの空きが10%を下回りました。アプリと Agent の使用量を見てください。 ${var.notify}"
  tags    = local.tags

  monitor_thresholds {
    critical = 0.1
  }
  notify_no_data = false
}

resource "datadog_monitor" "disk" {
  name    = "RaiseLine: ディスクが足りない"
  type    = "metric alert"
  query   = "avg(last_10m):avg:system.disk.in_use{service:raiseline} by {host,device} > 0.85"
  message = "ディスクの使用率が85%を超えました。ログと Docker のイメージを見てください。 ${var.notify}"
  tags    = local.tags

  monitor_thresholds {
    critical = 0.85
  }
  notify_no_data = false
}
