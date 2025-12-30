resource "aws_glue_catalog_database" "this" {
  name = var.database_name
}

data "aws_iam_policy_document" "assume" {
  statement {
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["glue.amazonaws.com"]
    }
    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "glue_role" {
  name               = "${var.name_prefix}-glue-role"
  assume_role_policy = data.aws_iam_policy_document.assume.json
  tags               = merge(var.tags, { Name = "${var.name_prefix}-glue-role" })
}

data "aws_iam_policy_document" "policy" {
  statement {
    sid    = "S3ReadCurated"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:ListBucket"
    ]
    resources = [
      var.curated_bucket_arn,
      "${var.curated_bucket_arn}/*"
    ]
  }

  statement {
    sid    = "GlueCatalogAccess"
    effect = "Allow"
    actions = [
      "glue:CreateTable",
      "glue:UpdateTable",
      "glue:GetDatabase",
      "glue:GetTable",
      "glue:GetTables",
      "glue:BatchGetPartition",
      "glue:BatchCreatePartition",
      "glue:CreatePartition",
      "glue:UpdatePartition",
      "glue:GetPartition",
      "glue:GetPartitions"
    ]
    resources = [
      "arn:aws:glue:*:*:catalog",
      "arn:aws:glue:*:*:database/${var.database_name}",
      "arn:aws:glue:*:*:table/${var.database_name}/*"
    ]
  }

  statement {
    sid    = "CloudWatchLogs"
    effect = "Allow"
    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]
    resources = [
      "arn:aws:logs:*:*:log-group:/aws-glue/crawlers:*"
    ]
  }
}

resource "aws_iam_role_policy" "inline" {
  role   = aws_iam_role.glue_role.id
  name   = "${var.name_prefix}-glue-policy"
  policy = data.aws_iam_policy_document.policy.json
}

resource "aws_glue_crawler" "curated" {
  name          = var.curated_crawler_name
  role          = aws_iam_role.glue_role.arn
  database_name = aws_glue_catalog_database.this.name
  table_prefix  = ""
  schedule      = "cron(0/30 * * * ? *)"

  recrawl_policy {
    recrawl_behavior = "CRAWL_EVERYTHING"
  }

  s3_target {
    path = "s3://${var.curated_bucket_name}/curated/curated_heatmap/"
  }

  tags = merge(var.tags, { Name = var.curated_crawler_name })
}
