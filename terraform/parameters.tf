resource "aws_ssm_parameter" "database_url" {
  name  = "${local.parameter_prefix}/database.url"
  type  = "String"
  value = "jdbc:mysql://${aws_db_instance.mysql.address}:${aws_db_instance.mysql.port}/${var.rds_db_name}?connectionTimeZone=%2B09:00&forceConnectionTimeZoneToSession=true&preserveInstants=true&characterEncoding=UTF-8"
}

resource "aws_ssm_parameter" "database_username" {
  name  = "${local.parameter_prefix}/database.username"
  type  = "String"
  value = var.rds_username
}

resource "aws_ssm_parameter" "database_secret_arn" {
  count = var.rds_manage_master_user_password ? 1 : 0

  name  = "${local.parameter_prefix}/database.secret-arn"
  type  = "String"
  value = aws_db_instance.mysql.master_user_secret[0].secret_arn
}

resource "aws_ssm_parameter" "redis_host" {
  name  = "${local.parameter_prefix}/redis.host"
  type  = "String"
  value = "localhost"
}

resource "aws_ssm_parameter" "redis_port" {
  name  = "${local.parameter_prefix}/redis.port"
  type  = "String"
  value = "6379"
}

resource "aws_ssm_parameter" "redis_database" {
  name  = "${local.parameter_prefix}/redis.database"
  type  = "String"
  value = "0"
}

resource "aws_ssm_parameter" "s3_bucket" {
  name  = "${local.parameter_prefix}/s3.bucket"
  type  = "String"
  value = aws_s3_bucket.storage.bucket
}

resource "aws_ssm_parameter" "cloudfront_url" {
  name  = "${local.parameter_prefix}/cloudfront.url"
  type  = "String"
  value = local.cloudfront_url
}

resource "aws_ssm_parameter" "admin_auth_username" {
  count = local.admin_auth_ssm_enabled ? 1 : 0

  name  = "${local.parameter_prefix}/flint.admin.auth.username"
  type  = "String"
  value = var.admin_auth_username
}

resource "aws_ssm_parameter" "admin_auth_password_hash" {
  count = local.admin_auth_ssm_enabled ? 1 : 0

  name  = "${local.parameter_prefix}/flint.admin.auth.password-hash"
  type  = "SecureString"
  value = var.admin_auth_password_hash
}
