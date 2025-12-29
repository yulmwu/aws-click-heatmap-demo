variable "name_prefix" {
  type        = string
  description = "Prefix for resource naming"
}

variable "app_name" {
  type        = string
  description = "Flink application name"
}

variable "runtime_environment" {
  type        = string
  description = "Flink runtime environment"
  default     = "FLINK-1_18"
}

variable "parallelism" {
  type        = number
  description = "Flink parallelism"
  default     = 1
}

variable "log_level" {
  type        = string
  description = "Application log level"
  default     = "INFO"
}

variable "artifact_bucket_name" {
  type        = string
  description = "S3 bucket to store the Flink application artifact."
}

variable "kinesis_stream_arn" {
  type        = string
  description = "ARN of the Kinesis stream to read from"
}

variable "curated_s3_path" {
  type        = string
  description = "S3 path for curated output data"
}

variable "aws_region" {
  type        = string
  description = "AWS region"
}

variable "tags" {
  type        = map(string)
  description = "Common tags"
  default     = {}
}
