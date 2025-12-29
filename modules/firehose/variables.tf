variable "name_prefix" {
  type        = string
  description = "Prefix for resource naming"
}

variable "kinesis_stream_arn" {
  type        = string
  description = "ARN of source Kinesis stream"
}

variable "raw_bucket_arn" {
  type        = string
  description = "ARN of raw data S3 bucket"
}

variable "raw_prefix" {
  type        = string
  description = "S3 prefix for raw data"
  default     = "raw/"
}

variable "buffer_size_mb" {
  type        = number
  description = "Buffer size in MB"
  default     = 64
}

variable "buffer_interval_seconds" {
  type        = number
  description = "Buffer interval in seconds"
  default     = 300
}

variable "compression_format" {
  type        = string
  description = "Compression format for S3 objects"
  default     = "UNCOMPRESSED"
}

variable "tags" {
  type        = map(string)
  description = "Common tags"
  default     = {}
}
