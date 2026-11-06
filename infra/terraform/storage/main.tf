# 投稿の画像を置く S3 バケット（F-14）。
# EC2・RDS の環境（1つ上のフォルダ）は down.ps1 で毎回消すが、画像は投稿と一緒に残したいので、
# このバケットだけ別の Terraform（state も別）で作り、down.ps1 では消さない。
# up.ps1 が毎回 apply する（もうあれば何もしない）。
# 料金: S3 の保存は 1GB あたり月 0.025 ドルほど。学習用の画像なら数円にもならない

terraform {
  required_version = ">= 1.11"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

variable "region" {
  description = "作成先のリージョン"
  type        = string
  default     = "ap-northeast-1"
}

variable "project" {
  description = "リソース名とタグに付ける名前（1つ上のフォルダの project と揃える）"
  type        = string
  default     = "sns-app"
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.project
      ManagedBy = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "images" {
  bucket = "${var.project}-images-${data.aws_caller_identity.current.account_id}"

  # 投稿の画像（ユーザーのデータ）なので、うっかり消さないようにする
  lifecycle {
    prevent_destroy = true
  }
}

# インターネットには公開しない。画像はアプリがログインを確かめてから返す
resource "aws_s3_bucket_public_access_block" "images" {
  bucket                  = aws_s3_bucket.images.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "images" {
  bucket = aws_s3_bucket.images.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# バケットの持ち主だけがファイルを持つ（ACL を使わない、今の AWS の推奨設定）
resource "aws_s3_bucket_ownership_controls" "images" {
  bucket = aws_s3_bucket.images.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# 暗号化されていない通信（http）での読み書きを断る
resource "aws_s3_bucket_policy" "images" {
  bucket = aws_s3_bucket.images.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [aws_s3_bucket.images.arn, "${aws_s3_bucket.images.arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })

  depends_on = [aws_s3_bucket_public_access_block.images]
}

output "images_bucket" {
  description = "投稿の画像を置くS3バケット"
  value       = aws_s3_bucket.images.bucket
}

# 監視の通知先。ここに置くのは、down.ps1 で消すとメールの確認を毎回やり直すことになるから
variable "alarm_email" {
  description = "アラームの通知先。terraform.tfvars に書く（公開リポジトリに入れない）。null なら通知しない"
  type        = string
  default     = null
}

resource "aws_sns_topic" "alarms" {
  name = "${var.project}-alarms"
}

resource "aws_sns_topic_subscription" "alarm_email" {
  count     = var.alarm_email == null ? 0 : 1
  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}
