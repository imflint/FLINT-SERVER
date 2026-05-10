variable "project" {
  description = "리소스 이름 접두사입니다."
  type        = string
  default     = "flint"
}

variable "environment" {
  description = "배포 환경입니다. 현재 deploy.sh가 dev Spring profile을 사용하므로 기본값은 dev입니다."
  type        = string
  default     = "dev"
}

variable "application_name" {
  description = "파라미터 스토어 경로에 사용할 스프링 애플리케이션 이름입니다."
  type        = string
  default     = "flint-api"
}

variable "aws_region" {
  description = "아마존 웹 서비스 리전입니다."
  type        = string
  default     = "ap-northeast-2"
}

variable "tags" {
  description = "모든 리소스에 추가로 적용할 태그입니다."
  type        = map(string)
  default     = {}
}

variable "vpc_cidr" {
  description = "가상 네트워크 CIDR 블록입니다."
  type        = string
  default     = "10.10.0.0/16"
}

variable "admin_ssh_cidrs" {
  description = "서버 SSH 접근을 허용할 CIDR 목록입니다. 가능하면 빈 값으로 두고 세션 매니저를 사용합니다."
  type        = list(string)
  default     = []
}

variable "ec2_instance_type" {
  description = "프리 티어 우선 서버 인스턴스 타입입니다. t4g.small은 ARM64를 사용합니다."
  type        = string
  default     = "t4g.small"
}

variable "ec2_architecture" {
  description = "머신 이미지 아키텍처입니다. t4g.*는 arm64, t3.*는 x86_64를 사용합니다."
  type        = string
  default     = "arm64"
}

variable "ec2_ami_id" {
  description = "선택적으로 고정할 머신 이미지 ID입니다. 비워두면 최신 Amazon Linux 2023 이미지를 선택합니다."
  type        = string
  default     = ""
}

variable "ec2_key_name" {
  description = "선택적으로 사용할 서버 key pair 이름입니다. 프리 티어 배포에서는 SSH보다 세션 매니저 사용을 권장합니다."
  type        = string
  default     = ""
}

variable "ec2_disable_api_termination" {
  description = "API 서버 EC2 인스턴스 종료 보호를 활성화할지 여부입니다. 운영 환경에서는 true를 권장하고, 비용 정리가 필요한 dev 환경에서는 false를 기본값으로 둡니다."
  type        = bool
  default     = false
}

variable "create_eip" {
  description = "API 서버에 고정 Elastic IP를 생성하고 연결할지 여부입니다. 연결된 EIP는 public IPv4 비용 구조를 유지하면서 DNS 연결을 안정화합니다."
  type        = bool
  default     = true
}

variable "ec2_volume_size" {
  description = "루트 EBS 볼륨 크기입니다. 단위는 GB입니다."
  type        = number
  default     = 30
}

variable "ec2_volume_type" {
  description = "루트 EBS 볼륨 타입입니다."
  type        = string
  default     = "gp3"
}

variable "rds_instance_class" {
  description = "관리형 데이터베이스 인스턴스 클래스입니다. 프리 티어 기준으로는 db.t4g.micro 또는 db.t3.micro를 권장합니다."
  type        = string
  default     = "db.t4g.micro"
}

variable "rds_engine_version" {
  description = "MySQL 엔진 버전입니다. null이면 AWS가 선택한 major 버전의 현재 기본값을 사용합니다."
  type        = string
  default     = null
}

variable "rds_db_name" {
  description = "초기 데이터베이스 이름입니다."
  type        = string
  default     = "flint"
}

variable "rds_username" {
  description = "관리형 데이터베이스 master 사용자 이름입니다."
  type        = string
  default     = "flint_admin"
}

variable "rds_password" {
  description = "관리형 데이터베이스 master 비밀번호입니다. rds_manage_master_user_password가 false일 때만 필요하며 provider 동작상 Terraform state에는 저장됩니다."
  type        = string
  default     = null
  sensitive   = true
}

variable "rds_manage_master_user_password" {
  description = "RDS가 Secrets Manager로 master password를 관리하도록 할지 여부입니다. 기본값 true에서는 rds_password를 Terraform에 전달하지 않습니다."
  type        = bool
  default     = true
}

variable "rds_allocated_storage" {
  description = "관리형 데이터베이스 스토리지 크기입니다. 단위는 GB이며, 20GB는 프리 티어 목표 범위입니다."
  type        = number
  default     = 20
}

variable "rds_storage_type" {
  description = "관리형 데이터베이스 스토리지 타입입니다. 프리 티어 목표에 맞춰 gp2를 사용합니다."
  type        = string
  default     = "gp2"
}

variable "rds_backup_retention_period" {
  description = "관리형 데이터베이스 자동 백업 보관 기간입니다. 단위는 일입니다."
  type        = number
  default     = 1
}

variable "rds_deletion_protection" {
  description = "관리형 데이터베이스 삭제 방지 활성화 여부입니다."
  type        = bool
  default     = false
}

variable "rds_skip_final_snapshot" {
  description = "데이터베이스 삭제 시 최종 스냅샷을 생략할지 여부입니다. 저비용 개발/프리 티어 환경에서는 true를 유지합니다."
  type        = bool
  default     = true
}

variable "storage_bucket_name" {
  description = "선택적으로 지정할 전역 고유 저장소 버킷 이름입니다."
  type        = string
  default     = null
}

variable "s3_cors_allowed_origins" {
  description = "스토리지 presigned upload 직접 호출을 허용할 origin 목록입니다."
  type        = list(string)
  default     = ["*"]
}

variable "cloudfront_price_class" {
  description = "CloudFront 가격 등급입니다. PriceClass_200은 아시아 엣지 위치를 포함하고, PriceClass_100은 더 저렴하지만 한국에서는 느릴 수 있습니다."
  type        = string
  default     = "PriceClass_200"
}

variable "cloudfront_aliases" {
  description = "스토리지 배포에 선택적으로 연결할 CloudFront alias 목록입니다."
  type        = list(string)
  default     = []
}

variable "cloudfront_certificate_arn" {
  description = "us-east-1 리전의 인증서 ARN입니다. cloudfront_aliases가 비어 있지 않을 때만 필요합니다."
  type        = string
  default     = null
}

variable "github_repo" {
  description = "배포 역할 사용을 허용할 GitHub 저장소입니다. 비워두면 GitHub OIDC 역할을 만들지 않습니다."
  type        = string
  default     = "imflint/FLINT-SERVER"
}

variable "github_repo_refs" {
  description = "배포 역할 사용을 허용할 GitHub OIDC subject ref 목록입니다."
  type        = list(string)
  default     = ["ref:refs/heads/develop", "ref:refs/heads/main"]
}

variable "create_github_oidc_provider" {
  description = "계정 단위 GitHub OIDC provider를 생성할지 여부입니다. AWS 계정에 이미 있으면 false로 설정합니다."
  type        = bool
  default     = true
}

variable "github_oidc_thumbprint_list" {
  description = "선택적으로 지정할 GitHub OIDC thumbprint 목록입니다. null이면 GitHub OIDC TLS 인증서 체인에서 계산합니다."
  type        = list(string)
  default     = null
}

variable "github_oidc_provider_arn" {
  description = "create_github_oidc_provider가 false일 때 사용할 기존 GitHub OIDC provider ARN입니다. null이면 표준 계정 ARN을 추론합니다."
  type        = string
  default     = null
}
