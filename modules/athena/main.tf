resource "aws_athena_workgroup" "this" {
  name          = var.athena_workgroup_name
  description   = "Workgroup for heatmap demo queries"
  force_destroy = true

  configuration {
    enforce_workgroup_configuration    = true
    publish_cloudwatch_metrics_enabled = true

    result_configuration {
      output_location = "s3://${var.results_bucket_name}/athena-results/"
      encryption_configuration {
        encryption_option = "SSE_S3"
      }
    }
  }

  tags = merge(var.tags, { Name = var.athena_workgroup_name })
}
