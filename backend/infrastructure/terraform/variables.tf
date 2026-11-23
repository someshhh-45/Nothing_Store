variable "aiven_api_token" { type = string sensitive = true }
variable "aiven_project" { type = string }
variable "cloud_name" { type = string default = "google-europe-west1" }
variable "prefix" { type = string default = "nothing-dev" }
variable "pg_plan" { type = string }
variable "kafka_plan" { type = string }
variable "opensearch_plan" { type = string }

# Development defaults allow single-broker plans. Production requires >=3 brokers.
variable "kafka_topic_partitions" { type = number default = 3 }
variable "kafka_topic_replication" { type = number default = 1 }
