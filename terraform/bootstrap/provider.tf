provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
      Component   = "terraform-state"
    }
  }
}

data "aws_caller_identity" "current" {}
