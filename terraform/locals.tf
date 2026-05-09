locals {
  name_prefix      = "${var.project}-${var.environment}"
  parameter_prefix = "/config/${var.application_name}/${var.environment}"

  common_tags = merge(
    {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
    },
    var.tags
  )

  public_subnet_cidr = cidrsubnet(var.vpc_cidr, 8, 1)
  db_subnet_cidrs    = [cidrsubnet(var.vpc_cidr, 8, 11), cidrsubnet(var.vpc_cidr, 8, 12)]

  storage_bucket_name = (
    var.storage_bucket_name != null && var.storage_bucket_name != ""
    ? var.storage_bucket_name
    : "${local.name_prefix}-storage-${data.aws_caller_identity.current.account_id}"
  )

  ec2_ami_id       = var.ec2_ami_id != "" ? var.ec2_ami_id : data.aws_ssm_parameter.al2023_ami[0].value
  cloudfront_url   = "https://${aws_cloudfront_distribution.storage.domain_name}"
  s3_origin_id     = "${local.name_prefix}-storage-origin"
  github_role_name = "${local.name_prefix}-github-actions-deploy"
}
