variable "project_name" {
  type        = string
  description = "Project name used for resource naming."
  default     = "heatmap-demo"
}

variable "aws_region" {
  type        = string
  description = "AWS region."
  default     = "ap-northeast-2"
}

variable "tags" {
  type        = map(string)
  description = "Additional tags."
  default     = {}
}

variable "kinesis_shard_count" {
  type        = number
  description = "Shard count for Kinesis Data Streams."
  default     = 1
}

variable "kinesis_retention_hours" {
  type        = number
  description = "Retention hours for Kinesis Data Streams (24-8760)."
  default     = 24
}

variable "firehose_buffer_size_mb" {
  type        = number
  description = "Buffer size (MB) for Firehose S3 delivery."
  default     = 64
}

variable "firehose_buffer_interval" {
  type        = number
  description = "Buffer interval (seconds) for Firehose S3 delivery."
  default     = 300
}

variable "raw_prefix" {
  type        = string
  description = "S3 prefix for raw data in the raw bucket."
  default     = "raw/"
}

variable "raw_error_prefix" {
  type        = string
  description = "S3 prefix for Firehose error output in the raw bucket."
  default     = "raw-errors/"
}

variable "raw_compression_format" {
  type        = string
  description = "Firehose S3 compression format. UNCOMPRESSED is simplest for PoC."
  default     = "UNCOMPRESSED"
}

variable "enable_versioning" {
  type        = bool
  description = "Enable S3 bucket versioning for all buckets."
  default     = false
}

variable "enable_glue" {
  type        = bool
  description = "Create Glue Database/Crawler for curated bucket."
  default     = true
}

variable "glue_database_name" {
  type        = string
  description = "Glue database name."
  default     = "heatmap_demo"
}

variable "curated_crawler_name" {
  type        = string
  description = "Glue crawler name for curated data."
  default     = "heatmap-demo-curated-crawler"
}

variable "athena_workgroup_name" {
  type        = string
  description = "Athena workgroup name."
  default     = "heatmap-demo-wg"
}

variable "enable_msf" {
  type        = bool
  description = "Create a Managed Service for Apache Flink (Kinesis Analytics v2) application (infra only)."
  default     = true
}

variable "msf_app_name" {
  type        = string
  description = "MSF application name."
  default     = "heatmap-demo-flink"
}

variable "msf_runtime" {
  type        = string
  description = "MSF runtime environment."
  default     = "FLINK-1_18"
}

variable "msf_parallelism" {
  type        = number
  description = "MSF parallelism (initial)."
  default     = 1
}

variable "msf_log_level" {
  type        = string
  description = "MSF log level."
  default     = "INFO"
}
