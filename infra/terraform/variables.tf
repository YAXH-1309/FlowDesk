variable "aws_region" {
  description = "Primary AWS region"
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment (prod, staging)"
  default     = "prod"
}

variable "availability_zones" {
  type    = list(string)
  default = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

variable "eks_node_groups" {
  type = map(object({
    instance_types = list(string)
    min_size       = number
    max_size       = number
    desired_size   = number
  }))
  default = {
    general = {
      instance_types = ["m5.xlarge"]
      min_size       = 3
      max_size       = 20
      desired_size   = 3
    }
  }
}

variable "domain_name" {
  description = "Public domain name for the platform"
  default     = "app.flowdesk.io"
}

variable "route53_zone_id" {
  description = "Route 53 hosted zone ID"
  default     = ""
}

variable "primary_alb_dns" {
  default = ""
}

variable "primary_alb_zone_id" {
  default = ""
}

variable "secondary_alb_dns" {
  default = ""
}

variable "secondary_alb_zone_id" {
  default = ""
}
