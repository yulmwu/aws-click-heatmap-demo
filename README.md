```shell
cp env/dev.tfvars.example env/dev.tfvars
cp env/example.backend.hcl env/dev.backend.hcl

# If you need prod and staging environments, uncomment the lines below
# cp env/prod.tfvars.example env/prod.tfvars
# cp env/staging.tfvars.example env/staging.tfvars
# cp env/example.backend.hcl env/prod.backend.hcl
# cp env/example.backend.hcl env/staging.backend.hcl

terraform init -backend-config="env/dev.backend.hcl"
terraform apply -var-file="env/dev.tfvars" --auto-approve
terraform output

terraform destroy -var-file="env/dev.tfvars" --auto-approve
```

For detailed options(variables), see [`env/dev.tfvars.example`](env/dev.tfvars.example).

![Architecture Diagram](assets/architecture.png)

_Draw.io file: [assets/architecture.drawio](assets/architecture.drawio)_

## Build Flink Application Artifact

```shell
# 1. Maven build
cd applications/flink-heatmap-job
mvn clean package

# 2. ZIP the JAR
cd target
zip flink-heatmap-job-1.0.0.zip flink-heatmap-job-1.0.0.jar

# 3. Copy to Terraform module artifacts directory
mkdir -p ../../../terraform/modules/msf/artifacts
cp flink-heatmap-job-1.0.0.zip ../../../terraform/modules/msf/artifacts/

# 4. Deploy with Terraform
cd ../../../terraform
terraform init -backend-config="env/dev.backend.hcl"
terraform apply -var-file="env/dev.tfvars" --auto-approve
```

## heatmap-click-producer

Create `applications/heatmap-click-producer/.env` based on `.env.example`:

| Environment Variable         | Terraform Output      | Description                                     |
| ---------------------------- | --------------------- | ----------------------------------------------- |
| `VITE_AWS_REGION`            | `aws_region`          | AWS region (e.g., `ap-northeast-2`)             |
| `VITE_KINESIS_STREAM_NAME`   | `kinesis_stream_name` | Kinesis Data Stream name                        |
| `VITE_PAGE_ID`               | N/A                   | Page identifier for click events (app-specific) |
| `VITE_AWS_ACCESS_KEY_ID`     | N/A                   | AWS access key (from IAM user/role)             |
| `VITE_AWS_SECRET_ACCESS_KEY` | N/A                   | AWS secret key (from IAM user/role)             |

**Get Terraform outputs:**

```shell
terraform output kinesis_stream_name
terraform output aws_region
```

## heatmap-athena-viewer

Create `applications/heatmap-athena-viewer/.env` based on `.env.example`:

| Environment Variable         | Terraform Output        | Description                                             |
| ---------------------------- | ----------------------- | ------------------------------------------------------- |
| `VITE_AWS_REGION`            | `aws_region`            | AWS region (e.g., `ap-northeast-2`)                     |
| `VITE_ATHENA_WORKGROUP`      | `athena_workgroup_name` | Athena workgroup name                                   |
| `VITE_GLUE_DATABASE`         | `glue_database_name`    | Glue database name                                      |
| `VITE_GLUE_TABLE`            | N/A                     | Glue table name (created by Glue Crawler)               |
| `VITE_AWS_ACCESS_KEY_ID`     | N/A                     | AWS access key (from IAM user/role)                     |
| `VITE_AWS_SECRET_ACCESS_KEY` | N/A                     | AWS secret key (from IAM user/role)                     |
| `VITE_AWS_SESSION_TOKEN`     | N/A                     | AWS session token (optional, for temporary credentials) |

**Get Terraform outputs:**

```shell
terraform output athena_workgroup_name
terraform output glue_database_name
terraform output aws_region
```

**Note:** The `VITE_GLUE_TABLE` must be set to the table name created by the Glue Crawler after it processes the curated data in S3. Run the Glue Crawler and check the Glue Console for the table name.
