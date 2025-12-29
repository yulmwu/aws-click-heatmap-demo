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
