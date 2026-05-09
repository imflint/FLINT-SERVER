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

output "rds_endpoint" {
  description = "관리형 MySQL 데이터베이스 엔드포인트입니다."
  value       = aws_db_instance.mysql.endpoint
  sensitive   = true
}

output "storage_bucket" {
  description = "스토리지 버킷 이름입니다."
  value       = aws_s3_bucket.storage.bucket
}

output "cloudfront_storage_domain_name" {
  description = "CloudFront 스토리지 배포 도메인 이름입니다."
  value       = aws_cloudfront_distribution.storage.domain_name
}

output "cloudfront_storage_url" {
  description = "CloudFront 스토리지 배포 URL입니다."
  value       = local.cloudfront_url
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

output "manual_secure_parameters" {
  description = "create_sensitive_ssm_parameters가 true가 아니라면 수동으로 생성해야 하는 보안 문자열 파라미터 목록입니다."
  value = [
    "${local.parameter_prefix}/database.password",
    "${local.parameter_prefix}/jwt.secret",
    "${local.parameter_prefix}/openai.key",
    "${local.parameter_prefix}/tmdb.api-key",
    "${local.parameter_prefix}/oauth.kakao.client-id",
    "${local.parameter_prefix}/oauth.kakao.client-secret",
    "${local.parameter_prefix}/oauth.kakao.redirect-uri",
    "${local.parameter_prefix}/oauth.apple.client-id",
  ]
}
