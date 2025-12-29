variable "athena_workgroup_name" {
  type        = string
  description = "Athena workgroup name"
}

variable "results_bucket_name" {
  type        = string
  description = "S3 bucket for Athena query results"
}

variable "tags" {
  type        = map(string)
  description = "Common tags"
  default     = {}
}
