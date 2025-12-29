output "workgroup_name" {
  value       = aws_athena_workgroup.this.name
  description = "Athena workgroup name"
}

output "workgroup_arn" {
  value       = aws_athena_workgroup.this.arn
  description = "Athena workgroup ARN"
}

output "output_location" {
  value       = aws_athena_workgroup.this.configuration[0].result_configuration[0].output_location
  description = "S3 output location for query results"
}
