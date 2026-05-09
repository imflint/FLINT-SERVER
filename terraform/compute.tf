data "aws_ssm_parameter" "al2023_ami" {
  count = var.ec2_ami_id == "" ? 1 : 0

  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-${var.ec2_architecture}"
}

resource "aws_instance" "api" {
  ami                         = local.ec2_ami_id
  instance_type               = var.ec2_instance_type
  subnet_id                   = aws_subnet.public.id
  vpc_security_group_ids      = [aws_security_group.ec2.id]
  iam_instance_profile        = aws_iam_instance_profile.ec2.name
  key_name                    = var.ec2_key_name != "" ? var.ec2_key_name : null
  associate_public_ip_address = true
  user_data_replace_on_change = true

  user_data = templatefile("${path.module}/scripts/user-data-free-tier.sh.tftpl", {
    app_name             = var.application_name
    environment          = var.environment
    parameter_prefix     = local.parameter_prefix
    redis_container_name = "${local.name_prefix}-redis"
  })

  root_block_device {
    volume_size           = var.ec2_volume_size
    volume_type           = var.ec2_volume_type
    encrypted             = true
    delete_on_termination = true
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  tags = {
    Name = "${local.name_prefix}-api"
  }
}

resource "aws_eip" "api" {
  count = var.create_eip ? 1 : 0

  domain = "vpc"

  tags = {
    Name = "${local.name_prefix}-api-eip"
  }
}

resource "aws_eip_association" "api" {
  count = var.create_eip ? 1 : 0

  allocation_id = aws_eip.api[0].id
  instance_id   = aws_instance.api.id
}
