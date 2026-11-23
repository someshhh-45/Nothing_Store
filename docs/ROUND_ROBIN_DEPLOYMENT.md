# NOTHING Marketplace — Nginx round robin and three Spring Boot replicas

**Implemented branch:** feat/nothing-marketplace-springboot.

## What is implemented

Browser → **Nginx** → **Spring Boot replica A / B / C** → common PostgreSQL.

Nginx 1.28 uses an equal-weight round-robin upstream, backed by Docker's internal DNS (app:8080), with membership re-resolved every 5 seconds, passive upstream failure handling, retry of eligible requests and persistent upstream connections. Three Java instances share PostgreSQL and the same JWT secret; there is no need for sticky sessions. The browser talks to only one public domain/port.

Each Java process exposes an internal liveness probe, has bounded HTTP/DB pools, a graceful shutdown interval, and a Docker restart policy. Auth endpoints have strict per-IP edge rate limiting (10 requests/minute with a burst of 10), while general API traffic is capped at 200 requests/second per IP with a burst of 500. Set limits for your measured production client mix; load tests from one IP can otherwise be intentionally throttled. The TLS cloud profile redirects HTTP to HTTPS, installs an actual PEM server cert, uses Secure JWT cookies and HSTS. Nginx access logs record the selected upstream IP without writing cookie values or query strings. Prometheus HA config uses DNS service discovery to scrape all app nodes.

**Important:** App-level redundancy is not whole-system high availability. All three replicas are on a *single Docker host*. Local demo Kafka, RabbitMQ, OpenSearch and PostgreSQL are still single-node. Even the Aiven profile has one local RabbitMQ and Nginx container. Cloud databases, brokers, host-level orchestration, backups and disaster recovery still require independent infrastructure decisions and verification.

## Local demo — Windows PowerShell

This creates a separate Docker Compose project called nothing-ha. It does NOT share the original backend project's Postgres volumes; to transfer existing data you need an explicit backup/restore process.

1. Pull the branch. Create backend/.env from backend/.env.example only if it does not already exist; fill LOCAL_DB_PASSWORD, RABBITMQ_PASSWORD, JWT_SECRET with real strong local secrets.
2. Stop your previous storefront if port 8000 is occupied, without deleting existing database volumes.
3. On a new HA demo database, seed catalog once with a single Java replica:

```powershell
git fetch origin
git switch feat/nothing-marketplace-springboot
git pull --ff-only origin feat/nothing-marketplace-springboot
$env:SEED_RETAIL_CATALOG="true"
docker compose -f backend/compose.ha.yml --env-file backend/.env up -d --build --scale app=1
```

4. Once http://localhost:8000/api/store/catalog has products, scale to three replicas:

```powershell
docker compose -f backend/compose.ha.yml --env-file backend/.env up -d --no-build --scale app=3
docker compose -f backend/compose.ha.yml --env-file backend/.env ps
docker compose -f backend/compose.ha.yml --env-file backend/.env logs --tail 120 frontend
```

In the logs, upstream=172.x.x.x:8080 should show three distinct peer IPs after several page/API requests. You can configure HTTP_MAX_THREADS, DB_POOL_MAX, DB_POOL_MIN_IDLE and other pool limits, but each new replica consumes additional PostgreSQL connections: keep **number of replicas × DB_POOL_MAX** below the actual DB server budget.

Run a **read-only** load baseline from the root:
```powershell
py -3 scripts/load-marketplace.py --requests 600 --concurrency 24 --base-url http://127.0.0.1:8000 --json-out ha-load-results.json
```

The automated script scripts/verify-round-robin.sh checks three unique upstream IPs, authenticates once and reuses the same signed JWT on many routed requests, **stops one backend container**, checks continued service, and starts it again. **Run that script only on a disposable local/CI stack**, never on production or shared databases.

## Aiven HTTPS staging deployment

1. Provision real Aiven PostgreSQL/Kafka/OpenSearch; select genuinely replicated multi-node plans for your availability target.
2. Use a **controlled single-writer schema migration process** before running more than one app. The earlier project uses Hibernate ddl-auto=update for most JPA entities; this must be replaced by audited migrations for production. The HA cloud profile uses Hibernate validate and disables demo seeding, so a fresh cloud database is **not automatically initialized**.
3. Copy backend/.env.aiven.ha.example to a private backend/.env.aiven and fill Aiven credentials, a random Base64 32+-byte JWT key, public HTTPS FRONTEND_URL, PEM TLS_CERT_PATH/TLS_KEY_PATH and the Aiven Postgres CA at AIVEN_PG_CA_PATH on the deployment host, RabbitMQ/Grafana credentials, SMTP and payment URLs. Never publish the private files or credentials.
4. Run preflight, then deploy (staging only until all checks pass):

```powershell
py -3 backend/scripts/validate-ha-env.py --env-file backend/.env.aiven
docker compose -f backend/compose.aiven.ha.yml --env-file backend/.env.aiven config --quiet
docker compose -f backend/compose.aiven.ha.yml --env-file backend/.env.aiven up -d --build --scale app=3
docker compose -f backend/compose.aiven.ha.yml --env-file backend/.env.aiven ps
```

The Aiven PostgreSQL JDBC URL must use sslmode=verify-full with sslrootcert=/etc/ssl/certs/aiven-pg-ca.pem. The CA file must be mounted from AIVEN_PG_CA_PATH, and the certificate hostname must match the Aiven host. Confirm Kafka/OpenSearch certificate chains are trusted by the Java runtime. The public ports are 80 and 443 on Nginx. Application ports 8080 and 8081 and Actuator metrics are **not bound** on the host. The older verify-aiven.py expects a host-mapped single app at 8090/8091, so it cannot directly verify this HA profile; use internal health checks through a secure operator connection. Use only valid trusted TLS certificates; the default placeholder URLs in examples never establish cloud readiness.

## Remaining production requirements

- Multi-host and availability-zone redundancy using Kubernetes/ECS or equivalent, plus external/managed load-balancer failover.
- Database and broker high-availability plans; RabbitMQ clustering or managed queue; Kafka consumer lag and poison-message alerting.
- Reviewed schema migrations, reproducible backup/restore drills, disaster recovery and certificate renewal.
- CSRF defenses for cookie-authenticated modifying requests (the application currently disables CSRF), account/device throttling, secrets management, audit logging and penetration testing.
- Production-like sustained and write-heavy checkout testing, payment reconciliation/refunds, Aiven live authentication and monitoring of p95/p99/5xx metrics.
- Provider/legal/compliance reviews. This is a **hardened, testable multi-replica deployment template**, not a production-certified system.

## CI evidence

The GitHub Actions workflow *NOTHING Marketplace three-replica round robin CI* starts one app, seeds the test database, scales to three apps, verifies round robin across 3 IPs, JWT cross-replica continuity, and request failover during one-app downtime. It also performs a read-only concurrent load benchmark and uploads results. Local measurements are not proof of cloud capacity.
