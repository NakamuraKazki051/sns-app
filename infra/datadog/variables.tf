variable "datadog_site" {
  description = "Datadog のサイト。ap1.datadoghq.com は日本"
  type        = string
  default     = "ap1.datadoghq.com"
}

variable "notify" {
  description = "通知先。例: @someone@example.com。公開リポジトリに入れないよう terraform.tfvars に書く"
  type        = string
}
