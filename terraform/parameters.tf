resource "aws_ssm_parameter" "database_url" {
  name  = "${local.parameter_prefix}/database.url"
  type  = "String"
  value = "jdbc:mysql://${aws_db_instance.mysql.address}:${aws_db_instance.mysql.port}/${var.rds_db_name}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
}

resource "aws_ssm_parameter" "database_username" {
  name  = "${local.parameter_prefix}/database.username"
  type  = "String"
  value = var.rds_username
}

resource "aws_ssm_parameter" "database_password" {
  count = var.create_sensitive_ssm_parameters ? 1 : 0

  name  = "${local.parameter_prefix}/database.password"
  type  = "SecureString"
  value = var.rds_password
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
