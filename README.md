## Real OpenSearch typo matching and order tracking

### Search Bluetooth with a spaced typo

OpenSearch now sends both the original phrase and a bounded, joined, fuzzy search for short split-word typos such as `blue teuth` -> `blueteuth` (up to two edits). PostgreSQL remains the inventory source; search hits are returned by OpenSearch, never by a hardcoded fallback.

```bash
git switch feat/nothing-marketplace-springboot
git pull --ff-only origin feat/nothing-marketplace-springboot
docker compose -f backend/compose.yml --env-file backend/.env up -d --build
bash scripts/repair-marketplace.sh
bash scripts/smoke-marketplace.sh
curl -fsSG http://localhost:8000/api/search/products --data-urlencode 'q=blue teuth' --data-urlencode 'size=20' | python3 -m json.tool
```

The Docker integration test requires actual Bluetooth product matches for the exact query `blue teuth`. If your browser continues to show old output, hard-refresh the storefront and check `http://localhost:8000/app.js` to verify you're using the updated frontend.

### Track a customer's order

Sign in, place an authenticated COD or verified Stripe order, then select **ORDERS -> TRACK YOUR ORDER**. Tracking is available only for the signed-in owner; other customers cannot view that order.

```http
GET /api/customer/orders/{orderId}/tracking
GET /api/admin/orders/{orderId}/tracking
PUT /api/admin/orders/{orderId}/tracking
Content-Type: application/json

{"stage":"CONFIRMED","note":"Order details verified"}
```

Supported milestones are **PLACED -> CONFIRMED -> PACKED -> SHIPPED -> OUT_FOR_DELIVERY -> DELIVERED**. Cancellation is allowed before shipment. Transitions cannot skip or reverse steps. Authorized staff can enter a carrier and tracking reference upon shipment and optionally a manually estimated delivery date. Each new step is recorded in PostgreSQL with a timestamp, and legacy orders do not get invented historical timestamps.

The admin website has a **FULFILLMENT** panel for these updates. Seller updates check whether the order contains products belonging to that seller; sellers cannot alter unrelated orders.

**Not real-time courier GPS/tracking:** the carrier reference, stages and dates are manually supplied by store staff. Carrier APIs, real scans, verified delivery evidence and automatic shipping notifications require a separate authenticated courier/shipping-provider integration.

Use `bash scripts/smoke-marketplace.sh` for read-only verification. To exercise the full tracking flow, create a demo customer order, open the customer timeline, log in as the configured admin, and advance the demo order. Do not mark real orders delivered as a test.

---
# NOTHING MARKET — Spring Boot marketplace (editorial redesign)

> **New development branch:** `feat/nothing-marketplace-springboot` forked from `backend`. Original `main`, `backend` and Python/FastAPI `feat/nothing` remain unchanged.

This branch is a **multicategory e-commerce demonstration** with a redesigned bold Nothing-style website and the original **Java Spring Boot** commerce services. It adds **56 product records** across Fashion, Toys & Games, Electronics (Tech), and Gadgets with **23 synthetic demo brands** and **28 distinct representative photo references**, on top of the original 46-product seed (approximately 102 items on a clean database). Prices, brands, stock and images in this seed are fictitious and illustrative, **not actual merchant offers**. Product photos are licensed from Unsplash but may **not** depict the exact named demo item. Do not advertise them as verified authentic inventory or take real payments until real seller/product validation is complete.

Read [Authentication and Marketplace Features](docs/AUTH_AND_MARKETPLACE_FEATURES.md), [Marketplace Setup](docs/README_MARKETPLACE.md), [Frontend Guide](nothing-coffee/README.md), and [Photo Credits](nothing-coffee/PHOTO_CREDITS.md).

## Marketplace recovery, size-specific SKUs and buying UX (November 21, 2026)

If the website shows **0 OBJECTS / NOTHING MATCHES** with only an **ALL** category chip, the local Spring Boot API may have returned **zero products** before demo seeding completed, or you may be running an old Compose image. That is **not** evidence that OpenSearch is returning no matches. Check the authoritative catalog first:

```bash
curl -fsS http://localhost:8000/api/store/catalog | python3 -c 'import json,sys; print(len(json.load(sys.stdin)), "database products")'
```

A fresh demo dataset contains **102 base items plus additional fashion size SKUs**. The frontend now retries zero catalog responses, reports database/seed failures explicitly, and has a retry control. To repair a local demo **without deleting accounts or orders**:

```bash
git fetch origin
git switch feat/nothing-marketplace-springboot
git pull --ff-only origin feat/nothing-marketplace-springboot
# If using a fresh clone, configure backend/.env first.
bash scripts/repair-marketplace.sh
bash scripts/smoke-marketplace.sh
```

The repair script safely recreates the Spring Boot and Nginx containers and verifies 100+ products, seven categories, 20+ brand labels and 20+ size-specific stock records. Do **not** run `docker compose down -v` or erase PostgreSQL volumes to fix the catalog.

### Sizes and fitting

Selected demo Fashion shoes and clothes have **separate PostgreSQL Product rows per size**, not client-only options. The size picker groups them visually. Shoe UK 6–10 options show approximate foot lengths in centimetres; apparel has S/M/L/XL. Cart persistence, backend stock validation, COD orders and Stripe checkout use the specific selected Product ID. Admins should replace approximate size guides, per-size stock, and images with verified supplier data before selling real merchandise. Merchant-managed products are not automatically modified by the demo variant seeder.

Other shopping UX: a bottom **Added to bag / View bag** notice after successful cart persistence, per-item quantity controls, selected addresses in checkout, COD/optional Stripe payment selection, clearer errors and account order history with purchased sizes. The storefront gets prices and stock from the Spring Boot API; changing client-side totals does not change checkout pricing.

### Payments

**COD** is the default demonstration checkout. **Stripe Hosted Checkout** is supported through the existing Spring Boot server endpoints, but remains disabled until you add your own `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET` in the private `backend/.env`. A signed Stripe webhook marks a paid order; a browser redirect does not prove payment. The local smoke script checks Stripe *configuration availability*, **not** that a card payment actually settled. For end-to-end payment testing use Stripe test-mode keys and forward the CLI to `http://localhost:8000/api/stripe/webhook`. Never commit keys or use live card details in the demo.

Production gaps still include official merchant/source data, accurate licensed product photos, genuine per-variant size charts and inventory, returns/refunds, shipping tracking, GST-compliant invoicing, full payment reconciliation, delivered emails/SMS, backups, abuse protections, live Aiven connection verification, and deployment security review.

---

### Quick start (Mac / Linux)

```bash
git clone -b feat/nothing-marketplace-springboot https://github.com/PunithNS-pixel/nothing.git
cd nothing
cp backend/.env.example backend/.env
# Set LOCAL_DB_PASSWORD, RABBITMQ_PASSWORD, JWT_SECRET, optional ADMIN_USERNAME and ADMIN_PASSWORD.
# Set SEED_RETAIL_CATALOG=true (already in the local Compose stack).
docker compose -f backend/compose.yml --env-file backend/.env up -d --build
```

Open **http://localhost:8000**. API: **http://localhost:8090**. Brand names are sent to the UI in `GET /api/store/catalog` and indexed as searchable text in OpenSearch. The JS brand filter refines **OpenSearch-backed** result IDs; COD/Stripe prices are always recalculated by Spring Boot. Local service startup and a live Stripe test transaction still need to be checked on your computer, and the full distributed stack is not exercised in GitHub's Java-only CI.

---

# Nothing Market — full-stack retail demo

A retail storefront for products across India: **electronics, fashion, home & living, grocery, and beauty**.

**GitHub branch:** `backend`. Existing coffee records are retained in PostgreSQL to avoid deleting users' data, but the retail catalog hides the legacy Coffee category.

## Architecture

```text
Browser storefront (Nothing Market)
       |
       +--> Spring Boot API (JWT cookie)
                 |
                 +--> PostgreSQL: customers, addresses, carts, orders, stock, transactional outbox
                 +--> Kafka outbox publisher -> Kafka topic nothing.events
                          |
                          +--> consumer -> OpenSearch product index updated
                          +--> order event -> RabbitMQ notification queue (log-only worker)
                 +--> OpenSearch: fuzzy name/description + category/city/price filters
                 +--> Micrometer /actuator/prometheus -> Prometheus -> Grafana
```

**Checkout correctness:** the order service reserves stock using a conditional PostgreSQL UPDATE inside the transaction and refuses overselling. The Kafka event is emitted from the outbox asynchronously; inventory is already reduced before commit, not deferred to the consumer. This avoids committing an order for stock you don't have.

## First run — local Docker

From the repository root:

```powershell
Copy-Item backend\.env.example backend\.env
notepad backend\.env
docker compose -f backend/compose.yml --env-file backend/.env up --build -d
docker compose -f backend/compose.yml --env-file backend/.env ps
```

Use strong random JWT secret and passwords. Default frontend: **http://localhost:8000**, backend API: **http://localhost:8090**, metrics: **http://localhost:8091**. Port values can be overridden with APP_HTTP_PORT and APP_METRICS_PORT. The frontend API URL is also configurable in the browser's localStorage key `nothing-api-url` (default 8090).

The development-only `SEED_RETAIL_CATALOG=true` adds 46 original sample products plus 56 optional multi-brand demo items in this branch to empty/migrated databases and emits product-change events. It does not delete existing data. Existing Coffee seed is disabled.

## Aiven services (required for hackathon submission)

1. Provision **Aiven PostgreSQL**, **Aiven Apache Kafka** and **Aiven OpenSearch** in your team's Aiven project.
2. Create a Kafka topic named `nothing.events`, with service credentials that can produce and consume.
3. Copy `backend/.env.aiven.example` to `backend/.env.aiven` and set all fields using the connection details in the Aiven console.
4. The JDBC URL must have `?sslmode=require` (or stricter certificate verification). Aiven Kafka uses `SASL_SSL` and `SCRAM-SHA-256` by default; set the full `AIVEN_KAFKA_SASL_JAAS_CONFIG`. OpenSearch requires an HTTPS endpoint and credentials.
5. Stop the local Compose stack (without `-v`) before starting the Aiven stack to avoid port collisions.
6. Start the Aiven stack:

```powershell
docker compose -f backend/compose.yml --env-file backend/.env down
docker compose -f backend/compose.aiven.yml --env-file backend/.env.aiven up --build -d
```

**Do not upload Aiven credentials, .env files, CA keys or Terraform state to GitHub.** For hosted deployment, use HTTPS behind a reverse proxy and set `JWT_COOKIE_SECURE=true`; configure FRONTEND_URL to your HTTPS origin.

## User journey

- Browse live catalog: `GET /api/store/catalog` (PostgreSQL).
- Search with typos, city, category and price filters: `GET /api/search/products?q=bluetoth&category=Electronics&city=Bengaluru` (OpenSearch).
- Sign up / sign in: `POST /api/auth/signup`, `POST /api/auth/signin`.
- Update per-user cart: `POST /api/cart/create` (PostgreSQL, immediately on every add/remove).
- Restore saved cart after login: `GET /api/carts`.
- Save delivery address: `POST /api/addresses`.
- COD checkout: `POST /api/order/users/payments/COD`.
- View your order history: `GET /api/customer/orders`.
- Verify outbox events:
  `docker exec backend-postgres-1 psql -U nothing -d nothing -c "select topic,event_key,published_at from nothing_outbox order by created_at desc limit 10;"`
  (this last command is for **local Compose only**).

Customer account passwords are stored as BCrypt hashes. Shopping carts are user-scoped in PostgreSQL; guest carts are browser-local until login. The frontend uses the backend-calculated price for checkout.

## Monitoring

Prometheus at http://localhost:9090 scrapes `app:8081/actuator/prometheus`. Grafana at http://localhost:3000 has a provisioned Nothing dashboard showing request rate, 5xx rate, JVM memory and Kafka lag. RabbitMQ is a notification queue; real email/SMS delivery isn't yet implemented.

## Deployment limitations

This is **a hackathon demo**, not a production-certified store. Features still needed for public production: verified online-payment webhooks, durable email/SMS notification provider, database migrations strategy and backups, CSRF protection for cookie auth, rate limiting, checkout idempotency and cancellation flows, inventory per-city warehouse (current city is a product-level attribute), distributed tracing, private network rules, horizontal-scale performance validation, and live Aiven end-to-end tests. Terraform examples are provided in `backend/infrastructure/terraform`, but no infrastructure is automatically provisioned without your Aiven project token/plan settings.

The GitHub CI workflow compiles, runs backend tests against an isolated PostgreSQL service, and validates frontend JavaScript syntax.

## Retailer accounts and GSTIN approval

The same account system supports regular shoppers and business buyers. A business buyer signs up or signs in as a customer, then uses **RETAILERS → APPLY AS A RETAILER** to submit registered business name and a compulsory 15-character GSTIN. A format check alone does NOT verify a real GST registration. No discount is granted until a store administrator checks the registration on the [official GST Portal > Search Taxpayer](https://www.gst.gov.in/) and marks the application APPROVED.

- `POST /api/retailer/apply` (authenticated): request approval, initial state `PENDING`.
- `GET /api/retailer/me` (authenticated): view own status and authorized discount percent.
- `GET /api/admin/retailers` (ROLE_ADMIN): list pending applications.
- `PUT /api/admin/retailers/{id}/review` (ROLE_ADMIN): approve or reject.
- Approved businesses receive `RETAILER_DISCOUNT_PERCENT` (default 10%) on supported items. The discount is enforced in PostgreSQL cart pricing and carries through order items and COD totals; pending and rejected accounts pay normal prices.
- In `backend/.env`, optionally set `ADMIN_USERNAME` (a **new** username not already registered) and a unique `ADMIN_PASSWORD` at least 12 characters. Restart the backend after setting them. The admin account is bootstrapped only on first startup and never resets an existing password. Once logged in, use the **APPROVALS** menu in the storefront.
- Retailers are wholesale *buyers*; they do not automatically gain the `ROLE_SELLER` permission to list inventory.

**Security note:** Approval is a manual workflow; official GSTN automated API lookup is not integrated. Do not approve applications based only on regex format. Price claims in the browser are not trusted; the server recalculates the discount, and the store must be deployed behind HTTPS with CSRF protection before public use.

## Product photography

The example product catalog uses externally hosted **Unsplash-licensed, illustrative photographs** (not exact branded product listings). Existing PostgreSQL records get a photo URL only if they do not already have one. Photos remain subject to third-party availability and should eventually be curated, licensed, optimized, stored in your own media/CDN pipeline and mapped to exact inventory SKUs. Source gallery and photographer details are maintained in `nothing-coffee/PHOTO_CREDITS.md`. Product photos are not stored as binary data in PostgreSQL, only their URLs.

## OpenSearch v2: infix + fuzzy search (2026)

Search is backed by **OpenSearch** rather than matching against the frontend array. The `nothing-products-v2` index combines:
- Character n-gram substring indexing (`min_gram=3`), so **buds** matches **Bluetooth Wireless Earbuds**, including words ending in `buds`.
- Conventional typo-tolerant full-text matching (`fuzziness=AUTO`), so **bluetoth** can match **Bluetooth**.
- Prefix matching to help product-name completion.
- Category, city, and numeric price filters enforced inside OpenSearch.
- A one-time automatic full reindex from PostgreSQL when this app instance first receives a search, so upgrades from an existing Docker volume or Aiven installation automatically populate the new index. Subsequent `PRODUCT_CHANGED` and `INVENTORY_CHANGED` Kafka messages keep the index up to date.
- The frontend debounces live typing and ignores stale responses when searches are entered quickly.

PowerShell verification after `docker compose ... up --build -d`:

```powershell
$b=Invoke-RestMethod 'http://localhost:8090/api/search/products?q=buds'
$b.hits.hits | ForEach-Object { $_._source.productName }
$f=Invoke-RestMethod 'http://localhost:8090/api/search/products?q=bluetoth&category=Electronics'
$f.hits.hits | ForEach-Object { $_._source.productName }
```

Expected example: `Bluetooth Wireless Earbuds`. The query will not invent a product such as Silkbuds unless that product actually exists in PostgreSQL and has been indexed.

To explicitly rebuild the search index, use the authenticated admin-only endpoint `POST /api/admin/search/reindex`. The v1 index remains untouched; v2 is a new index to support the new analysis settings. Production-scale catalogs should use versioned aliases and background reindexing rather than a synchronous first-search bootstrap.

## Same-origin checkout authentication and Stripe-hosted payments

**Important change:** The storefront at `http://localhost:8000` now calls `/api/...` on the **same origin**. Nginx proxies those requests to Spring Boot at `app:8080` on the Docker network. Rebuild/recreate the `frontend` service too; merely rebuilding `app` will not install the new reverse-proxy configuration. The Java API remains reachable directly at `http://localhost:8090` for debugging.

```powershell
git switch backend
git pull origin backend
docker compose -f backend/compose.yml --env-file backend/.env up -d --build --force-recreate app frontend
# Same-origin auth status: unauthenticated returns 401
curl.exe -i http://localhost:8000/api/auth/user
```

The end-to-end browser-cookie regression test `CustomerBrowserCheckoutTest` signs in, extracts the HttpOnly JWT cookie, saves a cart, creates an address and places a COD order. This protects against the observed "Full authentication is required" regressions.

**Stripe configuration (test mode first):** Add the following to `backend/.env`, *not* to Git:

```dotenv
STRIPE_SECRET_KEY=sk_test_YOUR_TEST_SECRET
STRIPE_WEBHOOK_SECRET=whsec_YOUR_LOCAL_STRIPE_CLI_SECRET
STRIPE_SUCCESS_URL=http://localhost:8000/?checkout=stripe-success
STRIPE_CANCEL_URL=http://localhost:8000/?checkout=stripe-cancel
```

Get your **test-mode secret key** from Stripe Dashboard. Install [Stripe CLI](https://docs.stripe.com/stripe-cli), authenticate locally with `stripe login`, and forward signed webhooks:

```powershell
stripe listen --forward-to localhost:8000/api/stripe/webhook
```

Copy the `whsec_` signing secret printed by the running Stripe CLI into `STRIPE_WEBHOOK_SECRET`. Recreate the app after changing environment variables:

```powershell
docker compose -f backend/compose.yml --env-file backend/.env up -d --force-recreate app frontend
```

Select **PAY SECURELY WITH STRIPE** after choosing a delivery address. The server reads authenticated cart items and PostgreSQL prices, creates a Stripe-hosted Checkout Session and redirects the browser to Stripe. The checkout success redirect is **not** accepted as proof of payment. Only an event signed with `STRIPE_WEBHOOK_SECRET` whose session is paid, INR, correct amount, and matches the stored attempt may create the order. The order uses PostgreSQL atomic stock reservation; the existing Kafka outbox emits order/inventory events.

Endpoints:

- `POST /api/checkout/stripe/session` — authenticated, body `{"addressId": 123}`, returns a hosted Stripe Checkout URL.
- `POST /api/stripe/webhook` — public endpoint but **Stripe-Signature verified**. Unauthenticated unsigned requests are rejected.
- `GET /api/checkout/stripe/status?sessionId=...` — authenticated and owner-scoped status. Never use browser redirects as evidence of payment.
- The old unsafe client-amount `/api/order/stripe-client-secret` has been removed. Public order creation still allows **COD only**; the Stripe order path is internal to the verified webhook.

**Payment limitations before production:** This implementation uses an immutable Stripe Checkout amount and an attempt record, but inventory is not reserved before payment. If stock becomes unavailable or the user's cart changes between opening Stripe and its paid webhook, the transaction rejects fulfillment and Stripe retries; an operator must investigate and potentially refund the payment through Stripe Dashboard. Automatic refund/reconciliation, cart locking or time-limited reservations, retry/error monitoring, webhook DLQ, PCI/compliance review, HTTPS, CSRF controls, and full live Stripe tests are needed before taking real money. Until then **use Stripe test-mode keys only**. Stripe keys are private, and a production webhook should be configured with your public HTTPS domain.

**Aiven:** The same Nginx proxy and Stripe variables are included in `backend/compose.aiven.yml`; this is configuration only, not proof of a live Aiven deployment.


## Cloud reliability and load testing

The Aiven live connectivity verifier, bounded concurrent HTTP benchmark, and disposable Kafka/OpenSearch/RabbitMQ recovery tests are documented in [Reliability, load and Aiven](docs/RELIABILITY_LOAD_AIVEN.md). Live provider credentials and production-scale capacity are not implied by local CI results.

## Three Spring Boot replicas and Nginx round robin

[See the new HA deployment and live failover guide](docs/ROUND_ROBIN_DEPLOYMENT.md) for round robin routing, HTTPS, cloud prerequisites and limitations. The original single-instance Docker profiles remain available.

## Order-confirmation emails (COD and verified Stripe)

The server now writes a **durable order email job** with each successful checkout and sends a customer-facing HTML + plaintext receipt via SMTP after commit, with RabbitMQ notifications, multi-replica-safe claims and persistent retries. Actual mail is disabled until real SMTP credentials are set privately. See [Order confirmation emails: configuration, testing and operations](docs/ORDER_CONFIRMATION_EMAIL.md).

## Google Cloud Gmail API for confirmation and OTP emails

You can choose the Google Cloud OAuth-powered Gmail API sender instead of SMTP. The existing transactional email queue, database retry state and HTML receipt still apply. Follow the [Google Cloud setup and macOS OAuth script](docs/GOOGLE_CLOUD_GMAIL_API.md). Real sending requires your own Gmail/Workspace account and OAuth consent; source code contains no credentials.
