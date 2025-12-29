data "aws_iam_policy_document" "assume_ecs_tasks" {
  statement {
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "producer" {
  name               = "${var.name_prefix}-producer-role"
  assume_role_policy = data.aws_iam_policy_document.assume_ecs_tasks.json
  tags               = merge(var.tags, { Name = "${var.name_prefix}-producer-role" })
}

data "aws_iam_policy_document" "producer_policy" {
  statement {
    effect = "Allow"
    actions = [
      "kinesis:PutRecord",
      "kinesis:PutRecords",
      "kinesis:DescribeStreamSummary",
      "kinesis:DescribeStream"
    ]
    resources = [var.kinesis_stream_arn]
  }
}

resource "aws_iam_role_policy" "producer_inline" {
  name   = "${var.name_prefix}-producer-policy"
  role   = aws_iam_role.producer.id
  policy = data.aws_iam_policy_document.producer_policy.json
}

resource "aws_iam_role" "backend" {
  name               = "${var.name_prefix}-backend-role"
  assume_role_policy = data.aws_iam_policy_document.assume_ecs_tasks.json
  tags               = merge(var.tags, { Name = "${var.name_prefix}-backend-role" })
}

data "aws_iam_policy_document" "backend_policy" {
  statement {
    effect = "Allow"
    actions = [
      "athena:StartQueryExecution",
      "athena:GetQueryExecution",
      "athena:GetQueryResults",
      "athena:StopQueryExecution",
      "athena:GetWorkGroup",
      "athena:ListWorkGroups"
    ]
    resources = [
      var.athena_workgroup_arn
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:ListBucket",
      "s3:PutObject"
    ]
    resources = [
      var.athena_results_bucket_arn,
      "${var.athena_results_bucket_arn}/*",
      var.raw_bucket_arn,
      "${var.raw_bucket_arn}/*",
      var.curated_bucket_arn,
      "${var.curated_bucket_arn}/*"
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "glue:GetDatabase",
      "glue:GetDatabases",
      "glue:GetTable",
      "glue:GetTables",
      "glue:GetPartition",
      "glue:GetPartitions"
    ]
    resources = concat(
      ["arn:aws:glue:*:*:catalog"],
      var.glue_database_arn != null ? [
        var.glue_database_arn,
        "${var.glue_database_arn}/*"
      ] : []
    )
  }
}

resource "aws_iam_role_policy" "backend_inline" {
  name   = "${var.name_prefix}-backend-policy"
  role   = aws_iam_role.backend.id
  policy = data.aws_iam_policy_document.backend_policy.json
}
