variable "region" {
  description = "作成先のリージョン"
  type        = string
  default     = "ap-northeast-1"
}

variable "project" {
  description = "リソース名とタグに付ける名前"
  type        = string
  default     = "sns-app"
}

variable "instance_type" {
  description = "EC2のインスタンスタイプ。無料枠の対象（t3.micro / t4g.micro など）から選ぶ"
  type        = string
  default     = "t3.micro"
}

variable "root_volume_size" {
  description = "ディスク容量（GB）。無料枠は合計30GBまで"
  type        = number
  default     = 20

  validation {
    condition     = var.root_volume_size >= 8 && var.root_volume_size <= 30
    error_message = "無料枠に収めるため、8〜30GBの範囲で指定してください。"
  }
}

variable "db_instance_class" {
  description = "RDSのインスタンスクラス。無料枠の対象（db.t4g.micro / db.t3.micro）から選ぶ"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_snapshot_identifier" {
  description = "RDSをこのスナップショットから復元して作る（up.ps1 が最新のものを自動で指定する）。null なら空のDBを作る"
  type        = string
  default     = null
}

variable "datadog_site" {
  description = "Datadog のサイト。ap1.datadoghq.com は日本"
  type        = string
  default     = "ap1.datadoghq.com"
}

variable "datadog_logs_enabled" {
  description = "ログも Datadog に送るか。Agent は API キーを登録したときだけ動く"
  type        = bool
  default     = true
}

variable "db_password_version" {
  description = "DBのパスワードを作り直したいときに1つ増やす（apply すると新しいパスワードに変わる）"
  type        = number
  default     = 1
}
