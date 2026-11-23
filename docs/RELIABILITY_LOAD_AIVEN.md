# NOTHING Marketplace — Aiven, load testing, and recovery

Target branch: `feat/nothing-marketplace-springboot`. This document separates **implemented code**, **CI-validated behavior**, and **cloud-provider validation requiring your own private credentials**.

## Architecture and consistency

`PostgreSQL (transactions + stock reservation + outbox) → scheduled publisher → Kafka → transactional inbox → OpenSearch and RabbitMQ.`

- PostgreSQL is the inventory/order authority. A successful order and its outbox event are committed in the same transaction. Search indexing is eventually consistent.
- The outbox stores attempt count and next retry time in PostgreSQL, claims rows atomically, and retries with exponential backoff. An event is marked published only after Kafka acknowledges it. Kafka producer idempotence plus `acks=all` is enabled; a process crash between broker acknowledgement and marking the row still permits a duplicate.
- The consumer atomically inserts into `nothing_consumed` with PostgreSQL's unique constraint **before** side effects; a transaction rollback clears that marker when indexing/queue publication fails. It retries transient consumer errors indefinitely, preventing silent message loss, at the cost of possibly blocking a partition on a permanently malformed event. External RabbitMQ and OpenSearch side effects are **at-least-once**, not exactly-once.
- PostgreSQL, broker/topic, search cluster and RabbitMQ have separate Actuator health component probes on the metrics port. Health replies do not disclose credentials. The Aiven Compose profile binds metrics and direct app ports to loopback.
- Operational limitations: single-node local Kafka/OpenSearch are *not* highly available, local RabbitMQ is one node, Aiven Terraform replication defaults to 1 for low-cost plans, and the application currently has no automated queue dead-letter monitoring or multi-region failover. Set `kafka_topic_replication=3` only on an Aiven Kafka plan with at least three brokers.

## Aiven: connect actual managed services

1. Provision Aiven PostgreSQL, Kafka and OpenSearch, or configure `backend/infrastructure/terraform` with your own Aiven project and API token. **This costs money on paid Aiven plans.** Terraform validation alone does not establish a live connection.
2. From the project root: `Copy-Item backend/.env.aiven.example backend/.env.aiven` (Windows PowerShell) or `cp backend/.env.aiven.example backend/.env.aiven` (Linux/macOS).
3. Put real values from the Aiven console into the PRIVATE `backend/.env.aiven`: database JDBC URL/user/password, Kafka broker and SCRAM JAAS credentials, HTTPS OpenSearch URL/user/password. Keep SMTP, JWT, RabbitMQ and Grafana credentials private. Do **not** commit the file or paste its contents in chat.
4. Start the managed-services profile (not the local Compose stack): `docker compose -f backend/compose.aiven.yml --env-file backend/.env.aiven up -d --build`.
5. Run `python3 backend/scripts/verify-aiven.py` from repo root. It validates non-placeholder configuration, tests broker/topic connectivity over SASL_SSL, verifies database and RabbitMQ health, performs an authenticated HTTPS OpenSearch health request, and exercises live catalog/search APIs. It **fails closed** if any dependency is down; a Compose config-only check is not a pass.
6. To stop **without deleting data**: `docker compose -f backend/compose.aiven.yml --env-file backend/.env.aiven down`. Do not run `down -v` on important data, and never destroy Aiven infrastructure without explicit approval.

Aiven's PostgreSQL `sslmode=require` encrypts transport but **does not strictly authenticate the server certificate**. For verified server identity, arrange a trusted Aiven CA within the app container and use `sslmode=verify-full&sslrootcert=...`. Test with your provider certificate before switching modes. Kafka `SASL_SSL` and OpenSearch `https://` are enforced in this profile.

The Aiven profile has an isolated Compose project name `nothing-aiven` and publishes the direct app/metrics/monitoring ports on localhost. The main storefront still uses port 8000; stop the local profile before starting the Aiven profile if ports conflict.

## Reproducible read-only load benchmark

After the local stack is healthy:

```powershell
python scripts/load-marketplace.py --base-url http://localhost:8000 --requests 600 --concurrency 24 --p95-ms 3500 --max-error-rate 0.01 --json-out load-results.json
```

It warms up and concurrently measures GET catalog/category/search requests, verifies HTTP success, and prints requests/sec, p50/p95/p99 latency, error count and threshold results. `p95` and error-rate thresholds cause a nonzero exit. It does not authenticate users, simulate checkouts, or write inventory data. This is a **bounded read-heavy baseline**, not proof of capacity under 10,000+ buyers or real Stripe traffic. CI runs the baseline on disposable GitHub-hosted infrastructure; run on your target Aiven cluster and under representative traffic for a real performance claim. Increase concurrency gradually while monitoring Hikari pool usage, JVM/CPU, Kafka lag and DB response time. The app's Tomcat threads and Hikari pool can be tuned with `HTTP_MAX_THREADS`, `HTTP_ACCEPT_COUNT` and `DB_POOL_MAX`.

## Disposable failure-recovery test

The recovery script **stops services**. **Never run this against production or a shared stack**:

```bash
CHAOS_ACK=ci-disposable bash scripts/chaos-marketplace.sh
```

In a throwaway local or CI Compose stack it stops Kafka, inserts a synthetic product-change outbox event, confirms catalog availability and durable retention, restarts Kafka and waits for publication/inbox processing. Then it restarts OpenSearch and RabbitMQ, proving that database catalog reads remain available and search recovers; finally it restarts the app and confirms database persistence. CI also tests consumer transaction rollback + replay idempotency. These drills validate specific failure paths, **not** regional disaster recovery, SMTP delivery or customer payment correctness. Set up Kafka consumer-lag alerts, dead-letter routing for poison events, RabbitMQ HA and backup restore testing before production deployment.

## Judge-ready proof

Show the GitHub Actions `Backend CI` and `NOTHING Marketplace Docker integration` runs, the JSON load benchmark artifact and chaos recovery logs. Claim only the actually observed concurrent request count, throughput, p95 and successful recovery checks. Aiven may be described as **ready for live verification**, not verified, until the real private Aiven profile passes `verify-aiven.py`.
