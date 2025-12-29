output "producer_role_arn" {
  value       = aws_iam_role.producer.arn
  description = "Producer IAM role ARN"
}

output "backend_role_arn" {
  value       = aws_iam_role.backend.arn
  description = "Backend IAM role ARN"
}
