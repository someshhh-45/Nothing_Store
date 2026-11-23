# Nothing Market — backend and administration

This backend belongs to the **Nothing Market multi-category retail store**. See the root README for the complete architecture and customer journey.

## Database administrator access

The local PostgreSQL container is configured with database `nothing` and user `nothing`, whose password is the private `LOCAL_DB_PASSWORD` in `backend/.env`. This is a database connection account and is **not the storefront admin login**.

The quickest administrative shell from repository root:

```powershell
docker compose -f backend/compose.yml --env-file backend/.env exec postgres psql -U nothing -d nothing
```

Inside psql, `\dt` lists tables; `SELECT count(*) FROM users;` counts user records. Use `\q` to exit. Do not show password hashes or personal information in demo screenshots.

For an optional local-only visual database interface, set unique `PGADMIN_EMAIL` / `PGADMIN_PASSWORD` in your private `backend/.env`, then run:

```powershell
docker compose -f backend/compose.yml --env-file backend/.env --profile dbadmin up -d pgadmin
```

Open http://localhost:5050 and sign in with `PGADMIN_EMAIL` and `PGADMIN_PASSWORD`. In pgAdmin choose **Add New Server**:

- Name: `Nothing Market Local`
- Connection hostname: `postgres` (service hostname within Docker networking)
- Port: `5432`
- Maintenance database: `nothing`
- Username: `nothing`
- Password: the private `LOCAL_DB_PASSWORD`

The pgAdmin web port is bound to `127.0.0.1`, not publicly exposed. Its own credentials do not grant access to PostgreSQL without the separate DB connection credentials. Do not expose pgAdmin to the public internet.

When using the Aiven deployment profile, start the optional `pgadmin` service using `backend/compose.aiven.yml` and `backend/.env.aiven`. Enter the **Aiven PostgreSQL host, port, database, user and password** from the Aiven console, and configure SSL mode **Require** in the pgAdmin server connection settings. Do **not** enter `postgres` as the host for an Aiven connection.

## Automated local integration checks

Run the read-only PowerShell diagnostics:

```powershell
powershell -ExecutionPolicy Bypass -File backend/scripts/verify-integrations.ps1
```

This checks Compose, PostgreSQL queries, application catalog, fuzzy and substring OpenSearch responses, Kafka topics and outbox delivery evidence, RabbitMQ broker health, Prometheus target status and Grafana health. It does not place test orders or delete data. It cannot verify Aiven simply by seeing local Compose containers: check the Aiven console and start the Aiven Compose profile separately.

To verify the order-to-Kafka-to-OpenSearch flow, place a test COD order, then re-run the diagnostics and compare order, outbox, consumed and inventory counts. Production checks should include authenticated browser testing and failure-recovery drills.

## Terraform

```powershell
cd backend/infrastructure/terraform
terraform init
terraform validate
terraform plan
```

Terraform requires an authorized Aiven API token and project configuration; `terraform validate` checks definitions but does not verify cloud resources have been provisioned. Terraform state can contain secrets; use a secure remote state backend. Prefer environment variables for your API token over committing Terraform variable files.

## Security boundaries

- Customer accounts, retailer approval and the store administrator account are application-level identities.
- PostgreSQL administrative access is privileged database access, not part of the public website.
- Aiven PostgreSQL `avnadmin` is a service-scoped database account, not a machine root or an Aiven organization admin.
- Credentials must remain in private `.env` files. Never paste them in issues, chat or commits.

## Expanded retail demo catalog (seed)

The seed now contains **46 named items** across Electronics, Home & Living, Fashion, Grocery and Beauty (14 existing plus 32 additional). They have distinct names, descriptions, prices, city labels and positive stock. The seed is idempotent: existing products, purchases, accounts and inventory are not reset or overwritten. New products are added on backend startup when `SEED_RETAIL_CATALOG=true`. The new items without an assigned photograph use existing category icons in the frontend; add licensed product photos later.

For local Docker use `git pull origin backend` then `docker compose -f backend/compose.yml --env-file backend/.env up -d --build --force-recreate app frontend`. Open `http://localhost:8000/api/store/catalog` to inspect live PostgreSQL product data. The storefront now routes API requests on the same origin (`/api`), rather than contacting port 8090 from the browser, to keep authentication cookies consistent.

COD now keeps the successful order confirmation separate from the subsequent optional catalog/order-history refresh. Stripe is offered only when `/api/checkout/stripe/config` confirms both configured Stripe test credentials, and it still requires an actual signed test webhook before an order is fulfilled. Do not use production Stripe keys before reconciliation/refund handling is complete.

## Storefront login and image improvements

Visitors now have separate **SIGN IN** and **CREATE ACCOUNT** buttons. Signed-in customers have **ACCOUNT** and one-click **SIGN OUT** buttons. Signing out clears the in-browser bag so the next visitor cannot see another customer's selection; the database cart stays associated with the original account. No one needs to type \u0060LOGOUT\u0060.

Product cards use individually chosen Unsplash photos where available, with distinct product-specific illustrations and descriptive labels for the remaining products. If an external image cannot load, the illustration automatically replaces it. The demo photos are representative, not photographs of actual fulfilment inventory or proof of product branding. Marketplace pictures from Amazon/Flipkart were **not** copied or hotlinked. The Unsplash License is at https://unsplash.com/license. For real sales, sellers should upload photographs of the specific products they will ship.

After pulling this commit, run \u0060docker compose -f backend/compose.yml --env-file backend/.env up -d --build --force-recreate app frontend\u0060. Catalog seeding only fills missing photo URLs and never overwrites established images or quantity values. Refresh \u0060http://localhost:8000\u0060 with Ctrl+Shift+R.
