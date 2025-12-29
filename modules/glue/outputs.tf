output "database_name" {
  value       = aws_glue_catalog_database.this.name
  description = "Glue database name"
}

output "database_arn" {
  value       = aws_glue_catalog_database.this.arn
  description = "Glue database ARN"
}

output "curated_crawler_name" {
  value       = aws_glue_crawler.curated.name
  description = "Glue crawler name"
}
