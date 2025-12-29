data "aws_iam_policy_document" "assume" {
  statement {
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["kinesisanalytics.amazonaws.com"]
    }
    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "msf_role" {
  name               = "${var.name_prefix}-msf-role"
  assume_role_policy = data.aws_iam_policy_document.assume.json
  tags               = merge(var.tags, { Name = "${var.name_prefix}-msf-role" })
}

data "aws_iam_policy_document" "msf_policy" {
  statement {
    sid    = "S3Access"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:ListBucket",
      "s3:DeleteObject"
    ]
    resources = [
      "arn:aws:s3:::${var.artifact_bucket_name}",
      "arn:aws:s3:::${var.artifact_bucket_name}/*"
    ]
  }

  statement {
    sid    = "KinesisRead"
    effect = "Allow"
    actions = [
      "kinesis:DescribeStream",
      "kinesis:DescribeStreamSummary",
      "kinesis:GetRecords",
      "kinesis:GetShardIterator",
      "kinesis:ListShards",
      "kinesis:ListStreams",
      "kinesis:SubscribeToShard"
    ]
    resources = [var.kinesis_stream_arn]
  }

  statement {
    sid    = "CloudWatchLogs"
    effect = "Allow"
    actions = [
      "logs:PutLogEvents",
      "logs:CreateLogStream",
      "logs:CreateLogGroup",
      "logs:DescribeLogStreams"
    ]
    resources = [
      "arn:aws:logs:*:*:log-group:/aws/kinesis-analytics/${var.app_name}:*"
    ]
  }
}

resource "aws_iam_role_policy" "inline" {
  name   = "${var.name_prefix}-msf-policy"
  role   = aws_iam_role.msf_role.id
  policy = data.aws_iam_policy_document.msf_policy.json
}

resource "aws_s3_object" "artifact" {
  bucket = var.artifact_bucket_name
  key    = "artifacts/${var.app_name}/flink-heatmap-job-1.0.0.zip"
  source = "${path.module}/artifacts/flink-heatmap-job-1.0.0.zip"
  etag   = fileexists("${path.module}/artifacts/flink-heatmap-job-1.0.0.zip") ? filemd5("${path.module}/artifacts/flink-heatmap-job-1.0.0.zip") : null

  lifecycle {
    precondition {
      condition     = fileexists("${path.module}/artifacts/flink-heatmap-job-1.0.0.zip")
      error_message = "Flink JAR artifact not found. Please build the Flink application first: cd applications/flink-heatmap-job && mvn clean package && cd target && zip flink-heatmap-job-1.0.0.zip flink-heatmap-job-1.0.0.jar && mkdir -p ../../../modules/msf/artifacts && cp flink-heatmap-job-1.0.0.zip ../../../modules/msf/artifacts/"
    }
  }
}

resource "aws_kinesisanalyticsv2_application" "this" {
  name                   = var.app_name
  runtime_environment    = var.runtime_environment
  service_execution_role = aws_iam_role.msf_role.arn

  application_configuration {
    application_code_configuration {
      code_content {
        s3_content_location {
          bucket_arn = "arn:aws:s3:::${var.artifact_bucket_name}"
          file_key   = aws_s3_object.artifact.key
        }
      }
      code_content_type = "ZIPFILE"
    }

    flink_application_configuration {
      parallelism_configuration {
        configuration_type  = "CUSTOM"
        parallelism         = var.parallelism
        parallelism_per_kpu = 1
      }
    }

    environment_properties {
      property_group {
        property_group_id = "FlinkApplicationProperties"
        property_map = {
          "KINESIS_STREAM_ARN" = var.kinesis_stream_arn
          "CURATED_S3_PATH"    = var.curated_s3_path
          "AWS_REGION"         = var.aws_region
        }
      }
    }
  }

  tags = merge(var.tags, { Name = var.app_name })
}
