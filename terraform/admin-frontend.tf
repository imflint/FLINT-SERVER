resource "aws_s3_bucket" "admin_frontend" {
  bucket = local.admin_frontend_bucket_name

  tags = {
    Name = local.admin_frontend_bucket_name
  }
}

resource "aws_s3_bucket_public_access_block" "admin_frontend" {
  bucket = aws_s3_bucket.admin_frontend.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "admin_frontend" {
  bucket = aws_s3_bucket.admin_frontend.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "admin_frontend" {
  bucket = aws_s3_bucket.admin_frontend.id

  rule {
    id     = "abort-incomplete-multipart-uploads"
    status = "Enabled"

    filter {
      prefix = ""
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

resource "aws_cloudfront_origin_access_control" "admin_frontend" {
  name                              = "${local.name_prefix}-admin-frontend-oac"
  description                       = "OAC for ${local.name_prefix} admin frontend bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "admin_frontend" {
  enabled             = true
  is_ipv6_enabled     = true
  comment             = "${local.name_prefix} admin frontend"
  default_root_object = "index.html"
  price_class         = var.cloudfront_price_class
  aliases             = local.admin_frontend_aliases

  origin {
    domain_name              = aws_s3_bucket.admin_frontend.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.admin_frontend.id
    origin_id                = local.admin_frontend_origin_id
  }

  default_cache_behavior {
    target_origin_id       = local.admin_frontend_origin_id
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true
    min_ttl                = 0
    default_ttl            = 0
    max_ttl                = 0

    forwarded_values {
      query_string = false

      cookies {
        forward = "none"
      }
    }
  }

  ordered_cache_behavior {
    path_pattern           = "/assets/*"
    target_origin_id       = local.admin_frontend_origin_id
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true
    min_ttl                = 0
    default_ttl            = 31536000
    max_ttl                = 31536000

    forwarded_values {
      query_string = false

      cookies {
        forward = "none"
      }
    }
  }

  custom_error_response {
    error_code            = 403
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 0
  }

  custom_error_response {
    error_code            = 404
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 0
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = length(local.admin_frontend_aliases) == 0
    acm_certificate_arn            = length(local.admin_frontend_aliases) > 0 ? var.admin_frontend_cloudfront_certificate_arn : null
    ssl_support_method             = length(local.admin_frontend_aliases) > 0 ? "sni-only" : null
    minimum_protocol_version       = length(local.admin_frontend_aliases) > 0 ? "TLSv1.2_2021" : "TLSv1"
  }

  depends_on = [
    aws_s3_bucket_public_access_block.admin_frontend,
    aws_s3_bucket_ownership_controls.admin_frontend,
  ]

  tags = {
    Name = "${local.name_prefix}-admin-frontend-cdn"
  }
}

data "aws_iam_policy_document" "admin_frontend_cloudfront" {
  statement {
    sid    = "AllowCloudFrontRead"
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.admin_frontend.arn}/*"]

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.admin_frontend.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "admin_frontend_cloudfront" {
  bucket = aws_s3_bucket.admin_frontend.id
  policy = data.aws_iam_policy_document.admin_frontend_cloudfront.json
}

resource "aws_route53_record" "admin_frontend_a" {
  count = var.admin_frontend_route53_zone_id != null && var.admin_frontend_route53_zone_id != "" && length(local.admin_frontend_aliases) > 0 ? 1 : 0

  zone_id = var.admin_frontend_route53_zone_id
  name    = local.admin_frontend_aliases[0]
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.admin_frontend.domain_name
    zone_id                = aws_cloudfront_distribution.admin_frontend.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "admin_frontend_aaaa" {
  count = var.admin_frontend_route53_zone_id != null && var.admin_frontend_route53_zone_id != "" && length(local.admin_frontend_aliases) > 0 ? 1 : 0

  zone_id = var.admin_frontend_route53_zone_id
  name    = local.admin_frontend_aliases[0]
  type    = "AAAA"

  alias {
    name                   = aws_cloudfront_distribution.admin_frontend.domain_name
    zone_id                = aws_cloudfront_distribution.admin_frontend.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "admin_api_a" {
  count = var.admin_api_route53_zone_id != null && var.admin_api_route53_zone_id != "" && var.admin_api_domain_name != "" ? 1 : 0

  zone_id = var.admin_api_route53_zone_id
  name    = var.admin_api_domain_name
  type    = "A"
  ttl     = 300
  records = [var.admin_create_eip ? aws_eip.admin[0].public_ip : aws_instance.admin.public_ip]
}
