variable "name_prefix" {
  type        = string
  description = "Prefix for resource naming"
}

variable "shard_count" {
  type        = number
  description = "Number of shards for Kinesis stream"
  default     = 1
}

variable "retention_hours" {
  type        = number
  description = "Data retention in hours"
  default     = 24
}

variable "tags" {
  type        = map(string)
  description = "Common tags"
  default     = {}
}
