resource "aws_db_subnet_group" "mysql" {
  name       = "${local.name_prefix}-mysql"
  subnet_ids = aws_subnet.database[*].id

  tags = {
    Name = "${local.name_prefix}-mysql"
  }
}

resource "aws_db_instance" "mysql" {
  identifier = "${local.name_prefix}-mysql"

  engine         = "mysql"
  engine_version = var.rds_engine_version
  instance_class = var.rds_instance_class

  db_name                     = var.rds_db_name
  username                    = var.rds_username
  password                    = var.rds_manage_master_user_password ? null : var.rds_password
  manage_master_user_password = var.rds_manage_master_user_password

  allocated_storage = var.rds_allocated_storage
  storage_type      = var.rds_storage_type
  storage_encrypted = true

  db_subnet_group_name   = aws_db_subnet_group.mysql.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period = var.rds_backup_retention_period
  deletion_protection     = var.rds_deletion_protection
  skip_final_snapshot     = var.rds_skip_final_snapshot
  copy_tags_to_snapshot   = true

  auto_minor_version_upgrade = true
  apply_immediately          = true

  tags = {
    Name = "${local.name_prefix}-mysql"
  }

  lifecycle {
    precondition {
      condition     = var.rds_manage_master_user_password || (var.rds_password != null && var.rds_password != "")
      error_message = "rds_manage_master_user_password가 false이면 rds_password를 설정해야 합니다."
    }
  }
}

resource "terraform_data" "disable_mysql_master_user_secret_rotation" {
  count = var.rds_manage_master_user_password && var.rds_disable_master_user_secret_rotation ? 1 : 0

  input = {
    region     = var.aws_region
    secret_arn = aws_db_instance.mysql.master_user_secret[0].secret_arn
  }

  # The AWS provider exposes the RDS-managed secret ARN, but not a declarative
  # switch for disabling that secret's automatic rotation.
  triggers_replace = {
    enforce_at = timestamp()
    secret_arn = aws_db_instance.mysql.master_user_secret[0].secret_arn
  }

  provisioner "local-exec" {
    command = "aws secretsmanager cancel-rotate-secret --region \"$AWS_REGION\" --secret-id \"$SECRET_ARN\" >/dev/null"

    environment = {
      AWS_REGION = self.input.region
      SECRET_ARN = self.input.secret_arn
    }
  }
}
