resource "aws_kinesis_stream" "this" {
  name             = "${var.name_prefix}-kds"
  shard_count      = var.shard_count
  retention_period = var.retention_hours

  stream_mode_details {
    stream_mode = "PROVISIONED"
  }

  encryption_type = "KMS"
  kms_key_id      = "alias/aws/kinesis"

  tags = merge(var.tags, { Name = "${var.name_prefix}-kds" })
}
