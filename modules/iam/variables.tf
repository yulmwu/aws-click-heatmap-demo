variable "name_prefix" {
  type        = string
  description = "Prefix for resource naming"
}

variable "kinesis_stream_arn" {
  type        = string
  description = "ARN of Kinesis stream"
}

variable "athena_workgroup_arn" {
  type        = string
  description = "ARN of Athena workgroup"
}

variable "athena_results_bucket_arn" {
  type        = string
  description = "ARN of Athena results S3 bucket"
}

variable "raw_bucket_arn" {
  type        = string
  description = "ARN of raw data S3 bucket"
}

variable "curated_bucket_arn" {
  type        = string
  description = "ARN of curated data S3 bucket"
}

variable "glue_database_arn" {
  type        = string
  description = "ARN of Glue database"
  default     = null
}

variable "tags" {
  type        = map(string)
  description = "Common tags"
  default     = {}
}
