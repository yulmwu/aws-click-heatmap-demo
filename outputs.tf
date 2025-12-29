output "aws_region" {
  value       = var.aws_region
  description = "AWS region"
}

output "kinesis_stream_name" {
  value       = module.kinesis.stream_name
  description = "Kinesis stream name"
}

output "kinesis_stream_arn" {
  value       = module.kinesis.stream_arn
  description = "Kinesis stream ARN"
}

output "kinesis_endpoint_url" {
  value       = "https://kinesis.${var.aws_region}.amazonaws.com"
  description = "Regional endpoint you call via AWS SDK (PutRecord/PutRecords)."
}

output "firehose_delivery_stream_name" {
  value       = module.firehose.delivery_stream_name
  description = "Firehose delivery stream name"
}

output "s3_raw_bucket" {
  value       = module.s3.raw_bucket_name
  description = "Raw data S3 bucket name"
}

output "s3_curated_bucket" {
  value       = module.s3.curated_bucket_name
  description = "Curated data S3 bucket name"
}

output "s3_athena_results_bucket" {
  value       = module.s3.athena_results_bucket_name
  description = "Athena results S3 bucket name"
}

output "athena_results_output_location" {
  value       = module.athena.output_location
  description = "Athena query results output location"
}

output "athena_workgroup_name" {
  value       = module.athena.workgroup_name
  description = "Athena workgroup name"
}

output "athena_workgroup_arn" {
  value       = module.athena.workgroup_arn
  description = "Athena workgroup ARN"
}

output "glue_database_name" {
  value       = var.enable_glue ? module.glue[0].database_name : null
  description = "Glue database name"
}

output "glue_curated_crawler_name" {
  value       = var.enable_glue ? module.glue[0].curated_crawler_name : null
  description = "Glue crawler name for curated data"
}

output "msf_application_name" {
  value       = var.enable_msf ? module.msf[0].application_name : null
  description = "MSF application name"
}

output "msf_application_arn" {
  value       = var.enable_msf ? module.msf[0].application_arn : null
  description = "MSF application ARN"
}
