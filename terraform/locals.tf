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
  admin_frontend_bucket_name = (
    var.admin_frontend_bucket_name != null && var.admin_frontend_bucket_name != ""
    ? var.admin_frontend_bucket_name
    : "${local.name_prefix}-admin-frontend-${data.aws_caller_identity.current.account_id}"
  )
  admin_frontend_certificate_arn = var.admin_frontend_cloudfront_certificate_arn == null ? "" : trimspace(var.admin_frontend_cloudfront_certificate_arn)
  admin_frontend_route53_zone_id = var.admin_frontend_route53_zone_id == null ? "" : trimspace(var.admin_frontend_route53_zone_id)
  admin_api_route53_zone_id      = var.admin_api_route53_zone_id == null ? "" : trimspace(var.admin_api_route53_zone_id)
  admin_api_dns_name             = var.admin_api_domain_name == null ? "" : trimspace(var.admin_api_domain_name)
  admin_frontend_custom_domain_aliases = (
    length(var.admin_frontend_cloudfront_aliases) > 0
    ? var.admin_frontend_cloudfront_aliases
    : [var.admin_frontend_domain_name]
  )
  admin_frontend_managed_certificate_enabled = (
    var.admin_frontend_create_cloudfront_certificate
    && local.admin_frontend_certificate_arn == ""
    && length(local.admin_frontend_custom_domain_aliases) > 0
  )
  admin_frontend_viewer_certificate_arn = (
    local.admin_frontend_certificate_arn != ""
    ? local.admin_frontend_certificate_arn
    : (
      local.admin_frontend_managed_certificate_enabled && local.admin_frontend_route53_zone_id != ""
      ? aws_acm_certificate_validation.admin_frontend[0].certificate_arn
      : (
        local.admin_frontend_managed_certificate_enabled && var.admin_frontend_attach_custom_domain
        ? aws_acm_certificate.admin_frontend[0].arn
        : ""
      )
    )
  )
  admin_frontend_aliases = (
    var.admin_frontend_attach_custom_domain
    ? local.admin_frontend_custom_domain_aliases
    : []
  )

  ec2_ami_id                      = var.ec2_ami_id != "" ? var.ec2_ami_id : data.aws_ssm_parameter.al2023_ami[0].value
  cloudfront_url                  = "https://${aws_cloudfront_distribution.storage.domain_name}"
  s3_origin_id                    = "${local.name_prefix}-storage-origin"
  github_role_name                = "${local.name_prefix}-github-actions-deploy"
  admin_frontend_origin_id        = "${local.name_prefix}-admin-frontend-origin"
  admin_frontend_github_role_name = "${local.name_prefix}-admin-frontend-github-actions-deploy"
  api_deploy_prefix               = "deploy/${var.application_name}"
  admin_deploy_prefix             = "deploy/${var.admin_application_name}"
  ecr_repository_name = (
    var.ecr_repository_name != null && var.ecr_repository_name != ""
    ? var.ecr_repository_name
    : "${local.name_prefix}-api"
  )
  admin_ecr_repository_name = (
    var.admin_ecr_repository_name != null && var.admin_ecr_repository_name != ""
    ? var.admin_ecr_repository_name
    : "${local.name_prefix}-admin-api"
  )
  admin_auth_ssm_enabled = (
    var.create_admin_auth_ssm_parameters
    && trimspace(var.admin_auth_username) != ""
    && trimspace(var.admin_auth_password_hash) != ""
  )

  discord_webhook_parameter_path = "${local.parameter_prefix}/discord.webhook.alert"
}
