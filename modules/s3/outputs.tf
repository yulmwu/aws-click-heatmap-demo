output "raw_bucket_name" {
  value       = aws_s3_bucket.raw.bucket
  description = "Raw data bucket name"
}

output "raw_bucket_arn" {
  value       = aws_s3_bucket.raw.arn
  description = "Raw data bucket ARN"
}

output "curated_bucket_name" {
  value       = aws_s3_bucket.curated.bucket
  description = "Curated data bucket name"
}

output "curated_bucket_arn" {
  value       = aws_s3_bucket.curated.arn
  description = "Curated data bucket ARN"
}

output "athena_results_bucket_name" {
  value       = aws_s3_bucket.athena_results.bucket
  description = "Athena results bucket name"
}

output "athena_results_bucket_arn" {
  value       = aws_s3_bucket.athena_results.arn
  description = "Athena results bucket ARN"
}
