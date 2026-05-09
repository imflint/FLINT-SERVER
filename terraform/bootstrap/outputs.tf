output "state_bucket_name" {
  description = "Terraform 원격 상태를 저장할 스토리지 버킷입니다."
  value       = aws_s3_bucket.state.bucket
}

output "lock_table_name" {
  description = "Terraform 상태 잠금을 위한 DynamoDB table입니다."
  value       = aws_dynamodb_table.lock.name
}

output "backend_config" {
  description = "메인 Terraform 스택의 backend.tf에 사용할 값입니다."
  value = {
    bucket         = aws_s3_bucket.state.bucket
    key            = "free-tier/terraform.tfstate"
    region         = var.aws_region
    dynamodb_table = aws_dynamodb_table.lock.name
    encrypt        = true
  }
}
