# Development and deployment

[Back to project README](../README.md)

## Local workflow

Follow the root README to clone main, configure `backend/.env` and start Compose. After a change:

```powershell
git pull --ff-only origin main
docker compose -f backend/compose.yml --env-file backend/.env up -d --build
docker compose -f backend/compose.yml --env-file backend/.env ps
docker compose -f backend/compose.yml --env-file backend/.env logs --tail=100 app
```

Use your configured remote name if it is not origin. Hard-refresh the browser after frontend changes.

## Verification

```powershell
cd backend
.\mvnw.cmd test
cd ..
node --check nothing-coffee/app.js
node --test tests/storefront-search.test.cjs
powershell -ExecutionPolicy Bypass -File backend/scripts/verify-integrations.ps1
```

Backend tests require their configured database and dependencies. On Linux/macOS use `./mvnw test` and the available shell diagnostics, including `bash scripts/smoke-marketplace.sh`. CI definitions are in `.github/workflows/`; their presence is not proof of a passing run.

## Deployment options

| Option | Repository configuration |
| --- | --- |
| Local full stack | backend/compose.yml |
| Managed Aiven services | backend/compose.aiven.yml |
| High availability | backend/compose.ha.yml and compose.aiven.ha.yml |
| Render | render.yaml and root Dockerfile |

The Render definition alone does not provision or verify the complete Kafka, RabbitMQ, OpenSearch and monitoring stack. Review its environment settings, persistence and demo seed options before deployment. Managed-service availability and pricing must be checked separately.

## Production release checklist

- [ ] Configure HTTPS, secure cookies, authorization and CSRF controls.
- [ ] Keep credentials in platform secret storage; rotate any exposed secrets.
- [ ] Verify migrations, backups and restore procedures.
- [ ] Configure seller and platform delivery zones and test strict checkout enforcement.
- [ ] Test authenticated cart, seller approval, product pages and recommendations.
- [ ] Verify concurrent inventory updates and multi-seller checkout.
- [ ] Verify signed Stripe test webhooks, reconciliation and refund procedures.
- [ ] Verify Kafka outbox retry, consumer idempotency and RabbitMQ notification delivery.
- [ ] Verify OpenSearch indexing, relevance and recovery.
- [ ] Configure real product data, tax and return policies before real sales.
- [ ] Restrict management endpoints; configure monitoring and alerts.
- [ ] Run CI, browser smoke tests and hosted end-to-end tests.

No item is marked complete solely because implementation files exist. Record actual test results and deployment URLs when verified.

## Troubleshooting

Check Compose service status and logs before changing configuration. Confirm the catalog endpoint returns data before diagnosing search. Do not delete PostgreSQL volumes as a repair shortcut. See [backend administration](../backend/README.md) and [reliability notes](RELIABILITY_LOAD_AIVEN.md) for additional diagnostics.
