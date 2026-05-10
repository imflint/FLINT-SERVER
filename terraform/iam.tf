data "aws_iam_policy_document" "ec2_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ec2" {
  name               = "${local.name_prefix}-ec2-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
}

data "aws_iam_policy_document" "ec2" {
  statement {
    sid    = "ReadAppParameters"
    effect = "Allow"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter/config/${var.application_name}/*"
    ]
  }

  dynamic "statement" {
    for_each = var.rds_manage_master_user_password ? [1] : []

    content {
      sid    = "ReadRdsManagedDatabaseSecret"
      effect = "Allow"
      actions = [
        "secretsmanager:DescribeSecret",
        "secretsmanager:GetSecretValue",
      ]
      resources = [
        aws_db_instance.mysql.master_user_secret[0].secret_arn
      ]
    }
  }

  statement {
    sid    = "StorageBucketList"
    effect = "Allow"
    actions = [
      "s3:ListBucket",
      "s3:GetBucketLocation",
    ]
    resources = [aws_s3_bucket.storage.arn]
  }

  statement {
    sid    = "StorageObjectAccess"
    effect = "Allow"
    actions = [
      "s3:AbortMultipartUpload",
      "s3:DeleteObject",
      "s3:GetObject",
      "s3:PutObject",
    ]
    resources = ["${aws_s3_bucket.storage.arn}/*"]
  }

  statement {
    sid    = "EcrAuthorization"
    effect = "Allow"
    actions = [
      "ecr:GetAuthorizationToken",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "EcrImagePull"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:DescribeImages",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [aws_ecr_repository.api.arn]
  }
}

resource "aws_iam_policy" "ec2" {
  name   = "${local.name_prefix}-ec2-policy"
  policy = data.aws_iam_policy_document.ec2.json
}

resource "aws_iam_role_policy_attachment" "ec2" {
  role       = aws_iam_role.ec2.name
  policy_arn = aws_iam_policy.ec2.arn
}

resource "aws_iam_role_policy_attachment" "ec2_ssm_managed" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${local.name_prefix}-ec2-profile"
  role = aws_iam_role.ec2.name
}

resource "aws_iam_openid_connect_provider" "github" {
  count = var.github_repo != "" && var.create_github_oidc_provider ? 1 : 0

  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = local.github_oidc_thumbprint_list

  lifecycle {
    ignore_changes = [thumbprint_list]
  }
}

data "tls_certificate" "github_oidc" {
  count = var.github_repo != "" && var.create_github_oidc_provider && var.github_oidc_thumbprint_list == null ? 1 : 0

  url = "https://token.actions.githubusercontent.com/.well-known/openid-configuration"
}

locals {
  github_oidc_thumbprint_list = (
    var.github_repo != "" && var.create_github_oidc_provider
    ? (
      var.github_oidc_thumbprint_list != null
      ? var.github_oidc_thumbprint_list
      : [data.tls_certificate.github_oidc[0].certificates[length(data.tls_certificate.github_oidc[0].certificates) - 1].sha1_fingerprint]
    )
    : []
  )

  github_oidc_provider_arn = (
    var.github_repo == ""
    ? null
    : (
      var.create_github_oidc_provider
      ? aws_iam_openid_connect_provider.github[0].arn
      : coalesce(
        var.github_oidc_provider_arn,
        "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/token.actions.githubusercontent.com"
      )
    )
  )
}

data "aws_iam_policy_document" "github_actions_assume_role" {
  count = var.github_repo != "" ? 1 : 0

  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = [for ref in var.github_repo_refs : "repo:${var.github_repo}:${ref}"]
    }
  }
}

resource "aws_iam_role" "github_actions" {
  count = var.github_repo != "" ? 1 : 0

  name               = local.github_role_name
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume_role[0].json
}

data "aws_iam_policy_document" "github_actions_deploy" {
  count = var.github_repo != "" ? 1 : 0

  statement {
    sid    = "SSMSendCommandDocument"
    effect = "Allow"
    actions = [
      "ssm:SendCommand",
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:ssm:${var.aws_region}::document/AWS-RunShellScript"
    ]
  }

  statement {
    sid    = "SSMSendCommandInstances"
    effect = "Allow"
    actions = [
      "ssm:SendCommand",
    ]
    resources = [
      aws_instance.api.arn
    ]
  }

  statement {
    sid    = "SSMReadCommandResult"
    effect = "Allow"
    actions = [
      "ssm:GetCommandInvocation",
      "ssm:ListCommandInvocations",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "EC2Describe"
    effect = "Allow"
    actions = [
      "ec2:DescribeInstances",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "DeployArtifactUpload"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:ListBucket",
      "s3:PutObject",
    ]
    resources = [
      aws_s3_bucket.storage.arn,
      "${aws_s3_bucket.storage.arn}/deploy/*",
    ]
  }

  statement {
    sid    = "EcrAuthorization"
    effect = "Allow"
    actions = [
      "ecr:GetAuthorizationToken",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "EcrImagePush"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:DescribeRepositories",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [aws_ecr_repository.api.arn]
  }
}

resource "aws_iam_policy" "github_actions_deploy" {
  count = var.github_repo != "" ? 1 : 0

  name   = "${local.name_prefix}-github-actions-deploy"
  policy = data.aws_iam_policy_document.github_actions_deploy[0].json
}

resource "aws_iam_role_policy_attachment" "github_actions_deploy" {
  count = var.github_repo != "" ? 1 : 0

  role       = aws_iam_role.github_actions[0].name
  policy_arn = aws_iam_policy.github_actions_deploy[0].arn
}
