terraform {
  required_version = ">= 1.6.0"
  required_providers { aiven = { source = "aiven/aiven", version = "~> 4.0" } }
}
provider "aiven" { api_token = var.aiven_api_token }
resource "aiven_pg" "db" {
  project = var.aiven_project
  cloud_name = var.cloud_name
  plan = var.pg_plan
  service_name = "${var.prefix}-pg"
}
resource "aiven_kafka" "events" {
  project = var.aiven_project
  cloud_name = var.cloud_name
  plan = var.kafka_plan
  service_name = "${var.prefix}-kafka"
}
resource "aiven_opensearch" "search" {
  project = var.aiven_project
  cloud_name = var.cloud_name
  plan = var.opensearch_plan
  service_name = "${var.prefix}-search"
}
resource "aiven_kafka_topic" "events" {
  project = var.aiven_project
  service_name = aiven_kafka.events.service_name
  topic_name = "nothing.events"
  partitions = var.kafka_topic_partitions
  replication = var.kafka_topic_replication
}
