resource "aws_athena_workgroup" "this" {
  name        = var.athena_workgroup_name
  description = "Workgroup for heatmap demo queries"

  configuration {
    result_configuration {
      output_location = "s3://${var.results_bucket_name}/athena-results/"
    }
  }

  tags = merge(var.tags, { Name = var.athena_workgroup_name })
}
