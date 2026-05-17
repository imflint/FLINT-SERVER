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

resource "aws_acm_certificate" "admin_frontend" {
  count = local.admin_frontend_managed_certificate_enabled ? 1 : 0

  provider = aws.us_east_1

  domain_name = local.admin_frontend_custom_domain_aliases[0]
  subject_alternative_names = slice(
    local.admin_frontend_custom_domain_aliases,
    1,
    length(local.admin_frontend_custom_domain_aliases),
  )
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "${local.name_prefix}-admin-frontend-certificate"
  }
}

resource "aws_route53_record" "admin_frontend_certificate_validation" {
  for_each = (
    local.admin_frontend_managed_certificate_enabled && local.admin_frontend_route53_zone_id != ""
    ? {
      for dvo in aws_acm_certificate.admin_frontend[0].domain_validation_options : dvo.domain_name => {
        name   = dvo.resource_record_name
        record = dvo.resource_record_value
        type   = dvo.resource_record_type
      }
    }
    : {}
  )

  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = local.admin_frontend_route53_zone_id
}

resource "aws_acm_certificate_validation" "admin_frontend" {
  count = (
    local.admin_frontend_managed_certificate_enabled && local.admin_frontend_route53_zone_id != ""
    ? 1
    : 0
  )

  provider = aws.us_east_1

  certificate_arn         = aws_acm_certificate.admin_frontend[0].arn
  validation_record_fqdns = [for record in aws_route53_record.admin_frontend_certificate_validation : record.fqdn]
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
    acm_certificate_arn            = length(local.admin_frontend_aliases) > 0 ? local.admin_frontend_viewer_certificate_arn : null
    ssl_support_method             = length(local.admin_frontend_aliases) > 0 ? "sni-only" : null
    minimum_protocol_version       = length(local.admin_frontend_aliases) > 0 ? "TLSv1.2_2021" : "TLSv1"
  }

  lifecycle {
    precondition {
      condition     = length(local.admin_frontend_aliases) == 0 || local.admin_frontend_viewer_certificate_arn != ""
      error_message = "A us-east-1 ACM certificate is required when admin frontend CloudFront aliases are set."
    }
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
  count = local.admin_frontend_route53_zone_id != "" && length(local.admin_frontend_aliases) > 0 ? 1 : 0

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
  count = local.admin_frontend_route53_zone_id != "" && length(local.admin_frontend_aliases) > 0 ? 1 : 0

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
  count = local.admin_api_route53_zone_id != "" && local.admin_api_dns_name != "" ? 1 : 0

  zone_id = var.admin_api_route53_zone_id
  name    = var.admin_api_domain_name
  type    = "A"
  ttl     = 300
  records = [var.admin_create_eip ? aws_eip.admin[0].public_ip : aws_instance.admin.public_ip]
}
