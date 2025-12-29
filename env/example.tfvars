project_name = "heatmap-demo"
aws_region   = "ap-northeast-2"
tags = {
  Project = "heatmap-demo"
}

kinesis_shard_count     = 1
kinesis_retention_hours = 24

firehose_buffer_size_mb  = 64
firehose_buffer_interval = 300
raw_prefix               = "raw/"
raw_compression_format   = "UNCOMPRESSED"

enable_glue          = true
glue_database_name   = "heatmap_demo"
curated_crawler_name = "heatmap-demo-curated-crawler"

athena_workgroup_name = "heatmap-demo-wg"

enable_msf      = true
msf_app_name    = "heatmap-demo-flink"
msf_runtime     = "FLINK-1_18"
msf_parallelism = 1
