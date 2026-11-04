# 投稿の画像（F-14）。バケット自体は storage/ の別の Terraform で作ってある（down.ps1 で消さないため）。
# ここでは、EC2 のアプリがそのバケットの post-images/ の中だけを読み書き・削除できるようにする

data "aws_s3_bucket" "images" {
  bucket = "${var.project}-images-${data.aws_caller_identity.current.account_id}"
}

resource "aws_iam_role_policy" "post_images" {
  name = "post-images"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = "${data.aws_s3_bucket.images.arn}/post-images/*"
      },
      {
        # 無い画像を読んだとき、「権限が無い(403)」ではなく「無い(404)」と返してもらうために要る
        Effect    = "Allow"
        Action    = "s3:ListBucket"
        Resource  = data.aws_s3_bucket.images.arn
        Condition = { StringLike = { "s3:prefix" = "post-images/*" } }
      },
    ]
  })
}
