variable "name_prefix" {
  type        = string
  description = "Prefix for resource naming"
}

variable "database_name" {
  type        = string
  description = "Glue catalog database name"
}

variable "curated_bucket_name" {
  type        = string
  description = "S3 bucket for curated data"
}

variable "curated_bucket_arn" {
  type        = string
  description = "ARN of curated data S3 bucket"
}

variable "curated_prefix" {
  type        = string
  description = "S3 prefix for curated data"
  default     = "curated/"
}

variable "curated_crawler_name" {
  type        = string
  description = "Glue crawler name"
}

variable "tags" {
  type        = map(string)
  description = "Common tags"
  default     = {}
}
