variable "project" {
  description = "리소스 이름 접두사입니다."
  type        = string
  default     = "flint"
}

variable "environment" {
  description = "상태 저장소 환경 이름입니다."
  type        = string
  default     = "dev"
}

variable "aws_region" {
  description = "아마존 웹 서비스 리전입니다."
  type        = string
  default     = "ap-northeast-2"
}

variable "state_bucket_name" {
  description = "선택적으로 지정할 전역 고유 Terraform state bucket 이름입니다."
  type        = string
  default     = null
}

variable "enable_state_bucket_kms" {
  description = "Terraform state bucket 암호화에 customer managed KMS key를 사용할지 여부입니다. 비용 최소화 dev 환경에서는 false를 기본값으로 둡니다."
  type        = bool
  default     = false
}

variable "state_kms_key_deletion_window_in_days" {
  description = "Terraform state KMS key 삭제 대기 기간입니다. 단위는 일입니다."
  type        = number
  default     = 7

  validation {
    condition     = var.state_kms_key_deletion_window_in_days >= 7 && var.state_kms_key_deletion_window_in_days <= 30
    error_message = "KMS key 삭제 대기 기간은 7일 이상 30일 이하여야 합니다."
  }
}
