variable "name_prefix" {
  type        = string
  description = "Prefix for resource naming"
}

variable "suffix" {
  type        = string
  description = "Random suffix for bucket naming"
}

variable "tags" {
  type        = map(string)
  description = "Common tags"
  default     = {}
}
