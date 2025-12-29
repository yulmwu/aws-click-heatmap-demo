variable "name_prefix" {
  type        = string
  description = "Prefix for resource naming"
}

variable "suffix" {
  type        = string
  description = "Random suffix for bucket naming"
}

variable "enable_versioning" {
  type        = bool
  description = "Enable S3 bucket versioning"
  default     = false
}

variable "tags" {
  type        = map(string)
  description = "Common tags"
  default     = {}
}
