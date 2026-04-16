terraform {
  required_version = ">= 1.7"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }
  }
  backend "s3" {
    bucket = "flowdesk-terraform-state"
    key    = "prod/terraform.tfstate"
    region = "us-east-1"
  }
}

provider "aws" {
  region = var.aws_region
}

# ── EKS Cluster ───────────────────────────────────────────────────────────────
module "eks" {
  source          = "./modules/eks"
  cluster_name    = "flowdesk-${var.environment}"
  cluster_version = "1.29"
  vpc_id          = module.vpc.vpc_id
  subnet_ids      = module.vpc.private_subnet_ids
  node_groups     = var.eks_node_groups
}

# ── VPC ───────────────────────────────────────────────────────────────────────
module "vpc" {
  source             = "./modules/vpc"
  name               = "flowdesk-${var.environment}"
  cidr               = "10.0.0.0/16"
  availability_zones = var.availability_zones
}

# ── RDS PostgreSQL Multi-AZ + 3 Read Replicas ─────────────────────────────────
module "rds" {
  source              = "./modules/rds"
  identifier          = "flowdesk-${var.environment}"
  engine_version      = "16.2"
  instance_class      = "db.r6g.xlarge"
  allocated_storage   = 100
  multi_az            = true
  replica_count       = 3
  subnet_ids          = module.vpc.private_subnet_ids
  vpc_id              = module.vpc.vpc_id
  db_name             = "flowdesk"
  db_username         = "flowdesk"
  db_password_secret  = aws_secretsmanager_secret.db_password.arn
}

# ── ElastiCache Redis Cluster ─────────────────────────────────────────────────
module "elasticache" {
  source             = "./modules/elasticache"
  cluster_id         = "flowdesk-${var.environment}"
  node_type          = "cache.r6g.large"
  num_cache_nodes    = 3
  subnet_ids         = module.vpc.private_subnet_ids
  vpc_id             = module.vpc.vpc_id
}

# ── Amazon MSK (Kafka) ────────────────────────────────────────────────────────
module "msk" {
  source         = "./modules/msk"
  cluster_name   = "flowdesk-${var.environment}"
  kafka_version  = "3.6.0"
  broker_count   = 3
  instance_type  = "kafka.m5.large"
  subnet_ids     = module.vpc.private_subnet_ids
  vpc_id         = module.vpc.vpc_id
}

# ── ECR Registry ──────────────────────────────────────────────────────────────
resource "aws_ecr_repository" "backend" {
  name                 = "flowdesk/backend"
  image_tag_mutability = "MUTABLE"
  image_scanning_configuration { scan_on_push = true }
}

resource "aws_ecr_repository" "frontend" {
  name                 = "flowdesk/frontend"
  image_tag_mutability = "MUTABLE"
  image_scanning_configuration { scan_on_push = true }
}

# ── S3 Buckets ────────────────────────────────────────────────────────────────
resource "aws_s3_bucket" "exports" {
  bucket = "flowdesk-exports-${var.environment}"
}

resource "aws_s3_bucket" "backups" {
  bucket = "flowdesk-backups-${var.environment}"
}

# S3 Cross-Region Replication for backups
resource "aws_s3_bucket_replication_configuration" "backups_crr" {
  bucket = aws_s3_bucket.backups.id
  role   = aws_iam_role.s3_replication.arn

  rule {
    id     = "replicate-all"
    status = "Enabled"
    destination {
      bucket        = aws_s3_bucket.backups_replica.arn
      storage_class = "STANDARD_IA"
    }
  }
}

resource "aws_s3_bucket" "backups_replica" {
  provider = aws.secondary
  bucket   = "flowdesk-backups-replica-${var.environment}"
}

# ── Secrets Manager ───────────────────────────────────────────────────────────
resource "aws_secretsmanager_secret" "db_password" {
  name = "flowdesk/${var.environment}/db-password"
}

resource "aws_secretsmanager_secret" "jwt_secret" {
  name = "flowdesk/${var.environment}/jwt-secret"
}

# ── ACM TLS Certificate ───────────────────────────────────────────────────────
resource "aws_acm_certificate" "main" {
  domain_name       = var.domain_name
  validation_method = "DNS"
  lifecycle { create_before_destroy = true }
}

# ── Route 53 Health Check + Failover ─────────────────────────────────────────
resource "aws_route53_health_check" "primary" {
  fqdn              = var.primary_alb_dns
  port              = 443
  type              = "HTTPS"
  resource_path     = "/actuator/health"
  failure_threshold = 3
  request_interval  = 30
}

resource "aws_route53_record" "primary" {
  zone_id = var.route53_zone_id
  name    = var.domain_name
  type    = "A"
  set_identifier = "primary"
  failover_routing_policy { type = "PRIMARY" }
  health_check_id = aws_route53_health_check.primary.id
  alias {
    name                   = var.primary_alb_dns
    zone_id                = var.primary_alb_zone_id
    evaluate_target_health = true
  }
}

resource "aws_route53_record" "secondary" {
  zone_id = var.route53_zone_id
  name    = var.domain_name
  type    = "A"
  set_identifier = "secondary"
  failover_routing_policy { type = "SECONDARY" }
  alias {
    name                   = var.secondary_alb_dns
    zone_id                = var.secondary_alb_zone_id
    evaluate_target_health = true
  }
}

# ── IAM Role for S3 Replication ───────────────────────────────────────────────
resource "aws_iam_role" "s3_replication" {
  name = "flowdesk-s3-replication-${var.environment}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "s3.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "s3_replication" {
  role = aws_iam_role.s3_replication.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:ReplicateObject", "s3:ReplicateDelete", "s3:ReplicateTags"]
      Resource = "${aws_s3_bucket.backups_replica.arn}/*"
    }]
  })
}
