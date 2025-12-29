output "application_name" {
  value       = aws_kinesisanalyticsv2_application.this.name
  description = "Flink application name"
}

output "application_arn" {
  value       = aws_kinesisanalyticsv2_application.this.arn
  description = "Flink application ARN"
}

output "execution_role_arn" {
  value       = aws_iam_role.msf_role.arn
  description = "MSF execution role ARN"
}

output "artifact_s3_key" {
  value       = aws_s3_object.artifact.key
  description = "S3 key for application artifact"
}
