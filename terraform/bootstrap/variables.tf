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
