# 監視とログ（CloudWatch）。考え方は docs/monitoring.md
# ここのものは down.ps1 で環境と一緒に消える。止めているあいだは、監視も動かない。
# 通知先の SNS トピックだけは storage/ にある（メールの確認を毎回しなくて済むように）
# 無料枠: ログ 5GB、アラーム 10個、カスタム指標 10個。ここで使うのはアラーム6個、指標6個

locals {
  metric_namespace = "SnsApp"

  # ログの中身から作る指標
  log_metrics = {
    Heartbeat    = "{ $.logger_name = \"com.snsapp.api.common.Heartbeat\" && $.level = \"INFO\" }"
    AppErrors    = "{ $.level = \"ERROR\" }"
    SlowRequests = "{ $.logger_name = \"com.snsapp.api.common.RequestLogFilter\" && $.level = \"WARN\" }"
    MailFailures = "{ $.logger_name = \"com.snsapp.api.auth.GmailVerificationMailer\" && $.level = \"ERROR\" }"
  }

  # 数が多いときに知らせるアラーム。データが無いのは問題なしとして扱う
  count_alarms = {
    errors = { name = "errors", description = "サーバーエラーが多い", metric = "AppErrors", period = 300, threshold = 5 }
    slow   = { name = "slow", description = "応答が遅い", metric = "SlowRequests", period = 600, threshold = 20 }
    mail   = { name = "mail", description = "確認メールを送れない", metric = "MailFailures", period = 300, threshold = 0 }
  }
}

data "aws_sns_topic" "alarms" {
  name = "${var.project}-alarms"
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/${var.project}/app"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_metric_filter" "app" {
  for_each = local.log_metrics

  name           = each.key
  log_group_name = aws_cloudwatch_log_group.app.name
  pattern        = each.value

  metric_transformation {
    name      = each.key
    namespace = local.metric_namespace
    value     = "1"
  }
}

# アラームの名前はメールの件名に出る。日本語は件名で崩れるので英字にして、説明を本文に出す
#
# アプリは1分ごとに生存のログを出す。届かなくなると「データ不足」に変わるので、それを通知する。
# 作った直後もデータ不足だが、変わったときにしか通知しない。起動すると OK に変わるので、OK のメールが1通届く。
# 回復の通知はこのアラームだけ。ほかにも付けると、起動のたびに OK のメールが6通届く
resource "aws_cloudwatch_metric_alarm" "app_down" {
  alarm_name          = "${var.project}-app-down"
  alarm_description   = "アプリが応答しない。コンテナと RDS を確かめる"
  namespace           = local.metric_namespace
  metric_name         = "Heartbeat"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 3
  comparison_operator = "LessThanThreshold"
  threshold           = 1
  treat_missing_data  = "missing"

  alarm_actions             = [data.aws_sns_topic.alarms.arn]
  insufficient_data_actions = [data.aws_sns_topic.alarms.arn]
  ok_actions                = [data.aws_sns_topic.alarms.arn]

  depends_on = [aws_cloudwatch_log_metric_filter.app]
}

resource "aws_cloudwatch_metric_alarm" "counts" {
  for_each = local.count_alarms

  alarm_name          = "${var.project}-${each.value.name}"
  alarm_description   = each.value.description
  namespace           = local.metric_namespace
  metric_name         = each.value.metric
  statistic           = "Sum"
  period              = each.value.period
  evaluation_periods  = 1
  comparison_operator = "GreaterThanThreshold"
  threshold           = each.value.threshold
  treat_missing_data  = "notBreaching"

  alarm_actions = [data.aws_sns_topic.alarms.arn]

  depends_on = [aws_cloudwatch_log_metric_filter.app]
}

# メモリとディスクは、EC2 の report-metrics が5分ごとに送る（user_data.sh.tftpl）
resource "aws_cloudwatch_metric_alarm" "memory" {
  alarm_name          = "${var.project}-memory"
  alarm_description   = "メモリが足りない"
  namespace           = local.metric_namespace
  metric_name         = "MemoryAvailablePercent"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  comparison_operator = "LessThanThreshold"
  threshold           = 10
  treat_missing_data  = "notBreaching"

  alarm_actions = [data.aws_sns_topic.alarms.arn]
}

resource "aws_cloudwatch_metric_alarm" "disk" {
  alarm_name          = "${var.project}-disk"
  alarm_description   = "ディスクが足りない"
  namespace           = local.metric_namespace
  metric_name         = "DiskUsedPercent"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  comparison_operator = "GreaterThanThreshold"
  threshold           = 85
  treat_missing_data  = "notBreaching"

  alarm_actions = [data.aws_sns_topic.alarms.arn]
}

# EC2 がログと指標を送るための権限（このロググループと、この名前空間だけ）
resource "aws_iam_role_policy" "monitoring" {
  name = "monitoring"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "${aws_cloudwatch_log_group.app.arn}:*"
      },
      {
        Effect    = "Allow"
        Action    = "cloudwatch:PutMetricData"
        Resource  = "*"
        Condition = { StringEquals = { "cloudwatch:namespace" = local.metric_namespace } }
      },
    ]
  })
}
