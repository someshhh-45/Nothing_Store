# NOTHING MARKET — Spring Boot marketplace branch

Branch: `feat/nothing-marketplace-springboot` (forked from `backend`, NOT the FastAPI `feat/nothing` branch).

**New authentication and customer feature documentation:** [AUTH_AND_MARKETPLACE_FEATURES.md](AUTH_AND_MARKETPLACE_FEATURES.md).

This branch retains the original Java Spring Boot backend and all existing auth, PostgreSQL carts/orders/addresses, COD, Stripe test-mode Checkout, retailer GSTIN applications, Kafka outbox, OpenSearch, RabbitMQ, Prometheus/Grafana and Terraform/Aiven configuration. It replaces the storefront with an original editorial NOTHING MARKET design and adds 56 new **synthetic demo listings** in Fashion (16), Toys & Games (16), Electronics (12) and Gadgets (12); the prior 46-category seed remains. The new data has 23 fictional demo brand names and 28 distinct representative photograph URLs. The combined demo catalog has approximately 102 products when both seeds are enabled and starts from an empty database.

**Important:** these aren't verified brand offers. Images from Unsplash are category-representative and may not match the listing. See `nothing-coffee/PHOTO_CREDITS.md`.

## Run on macOS

```bash
git clone -b feat/nothing-marketplace-springboot https://github.com/PunithNS-pixel/nothing.git
cd nothing
cp backend/.env.example backend/.env
open -e backend/.env
# Set LOCAL_DB_PASSWORD, RABBITMQ_PASSWORD, JWT_SECRET, and optional ADMIN_USERNAME/ADMIN_PASSWORD
# Keep SEED_RETAIL_CATALOG=true for the 46+56 demo listings.
docker compose -f backend/compose.yml --env-file backend/.env up -d --build
```

Website: **http://localhost:8000**; Spring Boot API: http://localhost:8090; Grafana: http://localhost:3000; Prometheus: http://localhost:9090. Do not start the FastAPI `feat/nothing` stack at the same time (port/service collisions).

## Self-verification

```bash
docker compose -f backend/compose.yml --env-file backend/.env ps
curl -fsS http://localhost:8000/api/store/catalog | python3 -c 'import json,sys; d=json.load(sys.stdin); print("items:",len(d)); print("categories:",sorted(set(x["category"] for x in d))); print("brands:",len(set(x["brand"] for x in d)))'
curl -fsSG http://localhost:8000/api/search/products --data-urlencode 'q=PLAYLAB' --data-urlencode 'size=100' | python3 -m json.tool
curl -fsSG http://localhost:8000/api/search/products --data-urlencode 'q=hoodie' | python3 -m json.tool
curl -i http://localhost:8000/api/auth/user  # 401 when logged out is expected
curl -fsS http://localhost:8091/actuator/health
```

Browser test: choose a category, choose one of 23 demo brands, open QUICK VIEW, add to bag, register/login, save a delivery address, place a cash-on-delivery test order, inspect order history and verify the retailer application/approval flow. Stripe is **off** until you configure test keys and a Stripe CLI webhook, then independently verify successful paid order creation.

**Do not use `docker compose down -v`** against any stack holding valuable orders or customers. The seeder is append-only and does not overwrite existing quantities/prices; a migrated existing DB will not be destroyed by demo seeding. For real commerce disable `SEED_RETAIL_CATALOG` and provision inventory only from your merchant systems.

## Backend/API compatibility

No existing auth or order path has been renamed. The storefront continues using same-origin `/api/...` via Nginx. Only additive API changes were made: `brand` field included in `GET /api/store/catalog`, `Product` entity and OpenSearch document. The browser brand filter is applied to the server-returned catalog/Search result IDs, not trusted for checkout pricing. PostgreSQL remains the authority for order totals, ownership and stock. A `ProductDTO.brand` field is also available to admin APIs.

## Readiness & gaps

This is an expanded working demo, not a production-certified marketplace. Java CI checks compilation/tests; full local integration, live Stripe webhooks, Aiven connections, reliable product photo rights/accuracy, tax, shipping, refunds, logistics, user verification, real notification delivery, seller onboarding, observability, privacy and backups must be validated before public launch. The consumer/outbox and billing backend remain from `backend` branch.