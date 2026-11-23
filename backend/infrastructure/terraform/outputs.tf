output "postgres_service" { value = aiven_pg.db.service_name }
output "kafka_service" { value = aiven_kafka.events.service_name }
output "opensearch_service" { value = aiven_opensearch.search.service_name }
