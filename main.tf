terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.27"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  backend "s3" {} # configured via env/*.backend.hcl
}

provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

resource "random_id" "suffix" {
  byte_length = 2
}

locals {
  name_prefix = var.project_name
  suffix      = random_id.suffix.hex
  tags = merge(var.tags, {
    Project   = var.project_name
    ManagedBy = "terraform"
  })
}

module "s3" {
  source = "./modules/s3"

  name_prefix       = local.name_prefix
  suffix            = local.suffix
  enable_versioning = var.enable_versioning
  tags              = local.tags
}

module "kinesis" {
  source = "./modules/kinesis"

  name_prefix     = local.name_prefix
  shard_count     = var.kinesis_shard_count
  retention_hours = var.kinesis_retention_hours
  tags            = local.tags
}

module "firehose" {
  source = "./modules/firehose"

  name_prefix             = local.name_prefix
  kinesis_stream_arn      = module.kinesis.stream_arn
  raw_bucket_arn          = module.s3.raw_bucket_arn
  raw_bucket_name         = module.s3.raw_bucket_name
  raw_prefix              = var.raw_prefix
  raw_error_prefix        = var.raw_error_prefix
  buffer_size_mb          = var.firehose_buffer_size_mb
  buffer_interval_seconds = var.firehose_buffer_interval
  compression_format      = var.raw_compression_format
  tags                    = local.tags
}

module "athena" {
  source = "./modules/athena"

  name_prefix           = local.name_prefix
  athena_workgroup_name = var.athena_workgroup_name
  results_bucket_name   = module.s3.athena_results_bucket_name
  tags                  = local.tags
}

module "glue" {
  source = "./modules/glue"
  count  = var.enable_glue ? 1 : 0

  name_prefix          = local.name_prefix
  database_name        = var.glue_database_name
  curated_bucket_name  = module.s3.curated_bucket_name
  curated_bucket_arn   = module.s3.curated_bucket_arn
  curated_prefix       = "curated/"
  curated_crawler_name = var.curated_crawler_name
  tags                 = local.tags
}

module "msf" {
  source = "./modules/msf"
  count  = var.enable_msf ? 1 : 0

  name_prefix         = local.name_prefix
  app_name            = var.msf_app_name
  runtime_environment = var.msf_runtime
  parallelism         = var.msf_parallelism
  log_level           = var.msf_log_level

  artifact_bucket_name = module.s3.curated_bucket_name
  kinesis_stream_arn   = module.kinesis.stream_arn
  curated_s3_path      = "s3://${module.s3.curated_bucket_name}/curated/"
  aws_region           = var.aws_region

  tags = local.tags
}

module "iam" {
  source = "./modules/iam"

  name_prefix               = local.name_prefix
  kinesis_stream_arn        = module.kinesis.stream_arn
  athena_workgroup_arn      = module.athena.workgroup_arn
  athena_results_bucket_arn = module.s3.athena_results_bucket_arn
  curated_bucket_arn        = module.s3.curated_bucket_arn
  raw_bucket_arn            = module.s3.raw_bucket_arn
  glue_database_arn         = var.enable_glue ? module.glue[0].database_arn : null

  tags = local.tags
}
