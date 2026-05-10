terraform {
  backend "s3" {
    bucket         = "flint-dev-terraform-state-566778528583"
    key            = "free-tier/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "flint-dev-terraform-lock"
    encrypt        = true
  }
}
