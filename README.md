## Prerequisites

```shell
# Setup Terraform backend (first time only)

aws s3 mb s3://heatmap-demo-terraform-state --region ap-northeast-2
aws dynamodb create-table --table-name TerraformStateLock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST --region ap-northeast-2
```

## Deployment

```shell
cp env/example.tfvars env/dev.tfvars
cp env/example.backend.hcl env/dev.backend.hcl

# If you need prod and staging environments, uncomment the lines below
# cp env/example.tfvars env/prod.tfvars
# cp env/example.tfvars env/staging.tfvars
# cp env/example.backend.hcl env/prod.backend.hcl
# cp env/example.backend.hcl env/staging.backend.hcl

terraform init -backend-config="env/dev.backend.hcl"
terraform apply -var-file="env/dev.tfvars" --auto-approve
terraform output

terraform destroy -var-file="env/dev.tfvars" --auto-approve
```

For detailed options(variables), see [env/example.tfvars](env/example.tfvars).

![Architecture Diagram](assets/architecture.png)

_Draw.io file: [assets/architecture.drawio](assets/architecture.drawio)_

## Build Flink Application Artifact

```shell
cd applications/flink-heatmap-job
mvn clean package

cd target
zip flink-heatmap-job-1.0.2.zip flink-heatmap-job-1.0.2.jar

mkdir -p ../../../modules/msf/artifacts
cp flink-heatmap-job-1.0.2.zip ../../../modules/msf/artifacts/

cd ../../..
```

```shell
terraform init -backend-config="env/dev.backend.hcl"
terraform apply -var-file="env/dev.tfvars"

aws kinesisanalyticsv2 start-application \
  --application-name $(terraform output -raw msf_application_name) \
  --run-configuration '{}'

# Wait for MSF application to be in Running state (can take 2-3 minutes)
aws kinesisanalyticsv2 describe-application \
  --application-name $(terraform output -raw msf_application_name) \
  --query 'ApplicationDetail.ApplicationStatus'
```

## heatmap-click-producer

Create `applications/heatmap-click-producer/.env` based on `.env.example`:

| Environment Variable         | Terraform Output      | Description                                     |
| ---------------------------- | --------------------- | ----------------------------------------------- |
| `VITE_AWS_REGION`            | `aws_region`          | AWS region (e.g., `ap-northeast-2`)             |
| `VITE_KINESIS_STREAM_NAME`   | `kinesis_stream_name` | Kinesis Data Stream name                        |
| `VITE_PAGE_ID`               | N/A                   | Page identifier for click events (app-specific) |
| `VITE_AWS_ACCESS_KEY_ID`     | N/A                   | AWS access key with full permissions            |
| `VITE_AWS_SECRET_ACCESS_KEY` | N/A                   | AWS secret key with full permissions            |

**Get Terraform outputs:**

```shell
terraform output kinesis_stream_name
terraform output aws_region
```

**Setup and run:**

```shell
cd applications/heatmap-click-producer
cp .env.example .env  # Edit
npm install
npm run start  # http://localhost:5173
```

## heatmap-athena-viewer

Create `applications/heatmap-athena-viewer/.env` based on `.env.example`:

| Environment Variable         | Terraform Output        | Description                                  |
| ---------------------------- | ----------------------- | -------------------------------------------- |
| `VITE_AWS_REGION`            | `aws_region`            | AWS region (e.g., `ap-northeast-2`)          |
| `VITE_ATHENA_WORKGROUP`      | `athena_workgroup_name` | Athena workgroup name                        |
| `VITE_GLUE_DATABASE`         | `glue_database_name`    | Glue database name                           |
| `VITE_GLUE_TABLE`            | N/A                     | Glue table name (e.g., `curated_heatmap`)    |
| `VITE_AWS_ACCESS_KEY_ID`     | N/A                     | AWS access key with full permissions         |
| `VITE_AWS_SECRET_ACCESS_KEY` | N/A                     | AWS secret key with full permissions         |

**Run Glue Crawler:**

Once the MSF application is in the 'Running' state and enough curated data has accumulated, run the Glue Crawler.

```shell
aws glue start-crawler --name $(terraform output -raw glue_curated_crawler_name)

aws glue get-crawler --name $(terraform output -raw glue_curated_crawler_name) \
  --query 'Crawler.State'

aws glue get-tables --database-name $(terraform output -raw glue_database_name) \
  --query 'TableList[*].Name'
```

**Setup and run:**

```shell
cd applications/heatmap-athena-viewer
cp .env.example .env  # Edit
npm install
npm run start  # http://localhost:8787
```

## Monitoring

```shell
aws kinesisanalyticsv2 describe-application \
  --application-name $(terraform output -raw msf_application_name)

aws s3 ls s3://$(terraform output -raw s3_curated_bucket)/curated/ --recursive

aws glue get-tables --database-name $(terraform output -raw glue_database_name)
```

## Cleanup

```shell
aws kinesisanalyticsv2 stop-application \
  --application-name $(terraform output -raw msf_application_name)

aws s3 rm s3://$(terraform output -raw s3_raw_bucket) --recursive
aws s3 rm s3://$(terraform output -raw s3_curated_bucket) --recursive
aws s3 rm s3://$(terraform output -raw s3_athena_results_bucket) --recursive

terraform destroy -var-file="env/dev.tfvars"
```

**Note:** The `VITE_GLUE_TABLE` value should be `curated_heatmap` (the crawler auto-creates this table name based on the S3 folder structure). If data is being written by Flink to `s3://BUCKET/curated/curated_heatmap/`, the Glue Crawler will create a table named `curated_heatmap`.
