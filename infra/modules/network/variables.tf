variable "name" {
  description = "Prefix used to identify the demo network resources."
  type        = string

  validation {
    condition     = length(trimspace(var.name)) > 0
    error_message = "name must not be empty."
  }
}

variable "availability_zones" {
  description = "Exactly two availability zones used by every subnet tier."
  type        = list(string)

  validation {
    condition     = length(var.availability_zones) == 2 && length(toset(var.availability_zones)) == 2
    error_message = "availability_zones must contain exactly two distinct availability zones."
  }
}

variable "vpc_cidr" {
  description = "IPv4 CIDR assigned to the VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "Two CIDRs for public subnets that host the NAT gateway."
  type        = list(string)
  default     = ["10.20.0.0/24", "10.20.1.0/24"]

  validation {
    condition     = length(var.public_subnet_cidrs) == 2
    error_message = "public_subnet_cidrs must contain exactly two CIDRs."
  }
}

variable "private_app_subnet_cidrs" {
  description = "Two CIDRs for private application subnets that host ECS tasks."
  type        = list(string)
  default     = ["10.20.16.0/20", "10.20.32.0/20"]

  validation {
    condition     = length(var.private_app_subnet_cidrs) == 2
    error_message = "private_app_subnet_cidrs must contain exactly two CIDRs."
  }
}

variable "isolated_data_subnet_cidrs" {
  description = "Two CIDRs for isolated data subnets that host RDS and Valkey."
  type        = list(string)
  default     = ["10.20.64.0/24", "10.20.65.0/24"]

  validation {
    condition     = length(var.isolated_data_subnet_cidrs) == 2
    error_message = "isolated_data_subnet_cidrs must contain exactly two CIDRs."
  }
}

variable "application_port" {
  description = "HTTP port exposed by the private application load balancer and ECS tasks."
  type        = number
  default     = 8080

  validation {
    condition     = var.application_port >= 1 && var.application_port <= 65535
    error_message = "application_port must be a valid TCP port."
  }
}

variable "rds_administrative_cidr" {
  description = "Optional single IPv4 /32 granted temporary direct PostgreSQL access."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.rds_administrative_cidr == null || (can(cidrhost(var.rds_administrative_cidr, 0)) && can(regex("^([0-9]{1,3}\\.){3}[0-9]{1,3}/32$", var.rds_administrative_cidr)))
    error_message = "rds_administrative_cidr must be a single IPv4 /32."
  }
}
