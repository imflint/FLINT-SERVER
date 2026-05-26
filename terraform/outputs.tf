output "ec2_instance_id" {
  description = "플린트 API 서버 인스턴스 ID입니다."
  value       = aws_instance.api.id
}

output "ec2_public_ip" {
  description = "플린트 API 서버 공개 IPv4 주소입니다."
  value       = var.create_eip ? aws_eip.api[0].public_ip : aws_instance.api.public_ip
}

output "ec2_public_dns" {
  description = "플린트 API 서버 공개 DNS 이름입니다."
  value       = aws_instance.api.public_dns
}

output "ec2_eip_allocation_id" {
  description = "Elastic IP 할당 ID입니다. create_eip이 false이면 null입니다."
  value       = var.create_eip ? aws_eip.api[0].allocation_id : null
}

output "admin_ec2_instance_id" {
  description = "플린트 관리자 API 서버 인스턴스 ID입니다."
  value       = aws_instance.admin.id
}

output "admin_ec2_public_ip" {
  description = "플린트 관리자 API 서버 공개 IPv4 주소입니다."
  value       = var.admin_create_eip ? aws_eip.admin[0].public_ip : aws_instance.admin.public_ip
}

output "admin_ec2_public_dns" {
  description = "플린트 관리자 API 서버 공개 DNS 이름입니다."
  value       = aws_instance.admin.public_dns
}

output "admin_ec2_eip_allocation_id" {
  description = "관리자 API 서버 Elastic IP 할당 ID입니다. admin_create_eip이 false이면 null입니다."
  value       = var.admin_create_eip ? aws_eip.admin[0].allocation_id : null
}

output "rds_endpoint" {
  description = "관리형 MySQL 데이터베이스 엔드포인트입니다."
  value       = aws_db_instance.mysql.endpoint
  sensitive   = true
}

output "rds_master_user_secret_arn" {
  description = "RDS managed master password를 사용할 때 생성되는 Secrets Manager secret ARN입니다. 사용하지 않으면 null입니다."
  value       = var.rds_manage_master_user_password ? aws_db_instance.mysql.master_user_secret[0].secret_arn : null
  sensitive   = true
}

output "storage_bucket" {
  description = "스토리지 버킷 이름입니다."
  value       = aws_s3_bucket.storage.bucket
}

output "admin_frontend_bucket" {
  description = "관리자 웹 프론트엔드 정적 파일 S3 버킷 이름입니다."
  value       = aws_s3_bucket.admin_frontend.bucket
}

output "ecr_repository_name" {
  description = "API Docker 이미지 ECR 저장소 이름입니다."
  value       = aws_ecr_repository.api.name
}

output "ecr_repository_url" {
  description = "API Docker 이미지 push/pull에 사용할 ECR 저장소 URL입니다."
  value       = aws_ecr_repository.api.repository_url
}

output "admin_ecr_repository_name" {
  description = "관리자 API Docker 이미지 ECR 저장소 이름입니다."
  value       = aws_ecr_repository.admin.name
}

output "admin_ecr_repository_url" {
  description = "관리자 API Docker 이미지 push/pull에 사용할 ECR 저장소 URL입니다."
  value       = aws_ecr_repository.admin.repository_url
}

output "cloudfront_storage_domain_name" {
  description = "CloudFront 스토리지 배포 도메인 이름입니다."
  value       = aws_cloudfront_distribution.storage.domain_name
}

output "cloudfront_storage_url" {
  description = "CloudFront 스토리지 배포 URL입니다."
  value       = local.cloudfront_url
}

output "admin_frontend_cloudfront_distribution_id" {
  description = "관리자 웹 프론트엔드 CloudFront 배포 ID입니다."
  value       = aws_cloudfront_distribution.admin_frontend.id
}

output "admin_frontend_cloudfront_domain_name" {
  description = "관리자 웹 프론트엔드 CloudFront 배포 도메인 이름입니다."
  value       = aws_cloudfront_distribution.admin_frontend.domain_name
}

output "admin_frontend_cloudfront_certificate_arn" {
  description = "관리자 웹 프론트엔드 CloudFront alias에 사용할 us-east-1 ACM 인증서 ARN입니다."
  value = (
    local.admin_frontend_certificate_arn != ""
    ? local.admin_frontend_certificate_arn
    : (
      local.admin_frontend_managed_certificate_enabled
      ? aws_acm_certificate.admin_frontend[0].arn
      : null
    )
  )
}

output "admin_frontend_cloudfront_certificate_dns_validation_records" {
  description = "Route53을 사용하지 않을 때 외부 DNS에 직접 등록해야 하는 ACM DNS 검증 레코드입니다."
  value = (
    local.admin_frontend_managed_certificate_enabled
    ? [
      for dvo in aws_acm_certificate.admin_frontend[0].domain_validation_options : {
        domain_name = dvo.domain_name
        name        = dvo.resource_record_name
        type        = dvo.resource_record_type
        value       = dvo.resource_record_value
      }
    ]
    : []
  )
}

output "admin_frontend_url" {
  description = "관리자 웹 프론트엔드 접속 URL입니다."
  value       = length(local.admin_frontend_aliases) > 0 ? "https://${local.admin_frontend_aliases[0]}" : "https://${aws_cloudfront_distribution.admin_frontend.domain_name}"
}

output "ssm_parameter_prefix" {
  description = "스프링 앱이 사용하는 파라미터 스토어 prefix입니다."
  value       = local.parameter_prefix
}

output "github_actions_role_arn" {
  description = "GitHub Actions 배포 역할 ARN입니다. github_repo가 비어 있으면 null입니다."
  value       = var.github_repo != "" ? aws_iam_role.github_actions[0].arn : null
  sensitive   = true
}

output "admin_frontend_github_actions_role_arn" {
  description = "관리자 웹 프론트엔드 GitHub Actions 배포 역할 ARN입니다. admin_frontend_github_repo가 비어 있으면 null입니다."
  value       = var.admin_frontend_github_repo != "" ? aws_iam_role.admin_frontend_github_actions[0].arn : null
  sensitive   = true
}

output "discord_alert_webhook_parameter_name" {
  description = "애플리케이션 오류 알림 Discord webhook URL을 SecureString으로 등록해야 하는 Parameter Store 이름입니다."
  value       = local.discord_webhook_parameter_path
}

output "discord_report_webhook_parameter_name" {
  description = "컬렉션 신고 Discord webhook URL을 SecureString으로 등록해야 하는 Parameter Store 이름입니다."
  value       = local.discord_report_webhook_parameter_path
}

output "manual_secure_parameters" {
  description = "Terraform state에 남기지 않기 위해 수동으로 생성해야 하는 보안 문자열 파라미터 목록입니다."
  value = concat(
    var.rds_manage_master_user_password ? [] : ["${local.parameter_prefix}/database.password"],
    [
      "${local.parameter_prefix}/jwt.secret",
      "${local.parameter_prefix}/openai.key",
      "${local.parameter_prefix}/tmdb.api-key",
      "${local.parameter_prefix}/oauth.kakao.client-id",
      "${local.parameter_prefix}/oauth.kakao.client-secret",
      "${local.parameter_prefix}/oauth.kakao.redirect-uri",
      "${local.parameter_prefix}/oauth.apple.client-id",
      local.discord_webhook_parameter_path,
      local.discord_report_webhook_parameter_path,
    ]
  )
}
