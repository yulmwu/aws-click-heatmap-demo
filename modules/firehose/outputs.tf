output "delivery_stream_name" {
  value       = aws_kinesis_firehose_delivery_stream.this.name
  description = "Firehose delivery stream name"
}

output "firehose_role_arn" {
  value       = aws_iam_role.firehose_role.arn
  description = "Firehose IAM role ARN"
}
