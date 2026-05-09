locals {
  name_prefix       = "${var.project}-${var.environment}"
  state_bucket_name = var.state_bucket_name != null && var.state_bucket_name != "" ? var.state_bucket_name : "${local.name_prefix}-terraform-state-${data.aws_caller_identity.current.account_id}"
  lock_table_name   = "${local.name_prefix}-terraform-lock"
}

resource "aws_s3_bucket" "state" {
  bucket = local.state_bucket_name

  tags = {
    Name = local.state_bucket_name
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket = aws_s3_bucket.state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_dynamodb_table" "lock" {
  name         = local.lock_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }
}
