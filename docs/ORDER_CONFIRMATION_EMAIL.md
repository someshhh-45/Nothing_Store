# NOTHING Marketplace — customer order confirmation email

Implemented in `feat/nothing-marketplace-springboot`. **No paid email provider or actual mailbox is configured by the code itself.** SMTP setup remains private to each developer/operator.

## What the customer receives

After a **successful COD order** or a **Stripe payment verified through a signed, paid webhook**, the customer receives a clean HTML email with a text-only alternative. It shows:

- Order number and confirmation, item names and quantities.
- Frozen item prices, coupon savings and the final INR total.
- Delivery address snapshotted at purchase time.
- Correct payment language: **COD: amount payable on delivery** or **Stripe: paid**.
- A prompt to view tracking from the account, accurately saying that courier GPS tracking is not integrated.

No email is triggered for an abandoned cart, failed payment, unsigned Stripe webhook, or checkout transaction that rolls back.

## How it works

```
Checkout (COD) / signed Stripe paid webhook
     |
     v
PostgreSQL transaction: order + order_items + stock reservation
                       + order_confirmation_emails(PENDING) + outbox(ORDER_PLACED)
     | COMMIT
     |                            (no SMTP call in the HTTP request)
     +--> Kafka event --> RabbitMQ durable queue --> notification worker
     |
     +--> 5-second DB retry poll ---------------> notification worker
                                                      |
                                                      v
                                              atomic claim on order_id
                                                      |
                                                      v
                                               authenticated SMTP
                                                      |
                                             SENT with sent_at
```

- The `order_confirmation_emails` row is inserted **in the same transaction** as the committed order. Each order has only one row; previously-placed orders are **not** retroactively scheduled or spammed.
- `ORDER_PLACED` is only created after the order exists inside that transaction. Sending occurs asynchronously.
- RabbitMQ wakes the worker, while periodic DB polling ensures sending can continue even if an event is delayed or the queue is temporarily unavailable.
- An atomic PostgreSQL claim serializes sends across three Spring Boot replicas. If SMTP fails, the row goes back to `PENDING` with exponential retry; it isn't silently marked sent.
- Once SMTP accepts the message, status becomes `SENT`. Repeated Rabbit events/replayed Stripe webhooks do not normally cause a second email.
- SMTP is not a true exactly-once transport. If SMTP accepts the email and the process crashes before writing `SENT`, a later retry **may generate a duplicate**. `SENT` means *accepted by the outgoing SMTP provider*, not guaranteed inbox delivery. Monitor bounces/suppression using the provider for production.

## Enable it for your local Windows PowerShell demo

1. Select a real SMTP provider. For a quick personal demo, Gmail SMTP with a Google **app password** (where supported) can work; for public ecommerce use a transactional mail service such as Amazon SES, Brevo or SendGrid with verified sender identity. Do not use your normal account password.
2. From repository root, pull and open your **private** `backend/.env`:

```powershell
git fetch origin
git switch feat/nothing-marketplace-springboot
git pull --ff-only origin feat/nothing-marketplace-springboot

if (!(Test-Path backend/.env)) { Copy-Item backend/.env.example backend/.env }
notepad backend/.env
```

3. Set values in `backend/.env` (values below are **examples only**, not working credentials):

```dotenv
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=you@example.com
SMTP_PASSWORD=YOUR_PRIVATE_APP_PASSWORD
SMTP_STARTTLS=true
EMAIL_FROM=you@example.com
MAIL_NOTIFICATIONS_ENABLED=true
MAIL_HEALTH_ENABLED=false
NOTIFICATION_POLL_MS=5000
```

Use the provider's exact server, username, password and permitted From address. The From address may need domain/sender verification. Do not add credentials to tracked files, screenshots, GitHub issues, logs or chat.

4. Recreate the app so Docker reads the new environment. Pick **one** of these profiles (do not start both on port 8000):

```powershell
# Existing local single-app setup
docker compose -f backend/compose.yml --env-file backend/.env up -d --build --force-recreate app

# OR, the three-replica setup:
docker compose -f backend/compose.ha.yml --env-file backend/.env up -d --build --scale app=3 --force-recreate app
```

5. Open http://localhost:8000, sign into a verified customer account, put an in-stock product in the bag, select a verified address, and place a COD order. For card checkout, use *real Stripe test-mode credentials and webhook signing*; don't assume a browser redirect proves payment.
6. The customer can open **ORDERS → CONFIRMATION EMAIL STATUS**. A row will move `PENDING → SENDING → SENT` if SMTP accepts it. If mail is off, it remains pending. Check inbox and spam/junk folder.

The email is sent to the **verified customer login email associated with the order**, not an arbitrary address entered during checkout.

## Operator diagnostics

The app logs an order ID after SMTP accepts a message, without logging the recipient/password:

```powershell
docker compose -f backend/compose.yml --env-file backend/.env logs --tail 100 app
```

Inspect durable state through PostgreSQL (safe: no email passwords exposed):

```powershell
docker compose -f backend/compose.yml --env-file backend/.env exec postgres psql -U nothing -d nothing -c "select order_id,status,attempts,sent_at,last_error_code from order_confirmation_emails order by order_id desc limit 20;"
```

Admin endpoints, protected by `ROLE_ADMIN`:

- `GET /api/admin/orders/{id}/confirmation`: delivery status.
- `POST /api/admin/orders/{id}/confirmation/retry`: make an unsent, pending confirmation eligible for immediate retry. Rejects already-`SENT` confirmations to prevent resending.

Buyer endpoint requires login and ownership of the order:

- `GET /api/customer/orders/{id}/confirmation`

Possible statuses are `PENDING`, `SENDING`, `SENT`, or `NOT_SCHEDULED` for older orders.

### Troubleshooting

- `PENDING`, `sendingEnabled=false`: set `MAIL_NOTIFICATIONS_ENABLED=true`, then recreate app containers; it is **not enough** to change the .env file without restarting.
- `PENDING`, repeated attempts: check SMTP host/port, provider credentials, verified sender, outbound firewall and rate limits. The `last_error_code` DB column contains only a generic exception class, not the provider's secret response.
- SMTP accepts email but inbox empty: provider may place it in spam, suppress it or reject it asynchronously; inspect provider logs. `SENT` is not final delivery proof.
- Only after provider is reachable should you turn on mail notifications in Aiven; do not put the SMTP password in GitHub.
- The baseline `MAIL_HEALTH_ENABLED=false` avoids failing store health checks during transient SMTP outages. This does **not** replace monitoring for stuck pending email jobs.

## Testing / production limitations

CI tests order-job atomicity for COD and signed Stripe, receipt escaping and totals, asynchronous worker deduplication, SMTP failure retry state, and access permissions. They use **mocked SMTP** and do not send real customer emails. A real mailbox end-to-end test is still required with your private SMTP credentials.

For production, add provider bounce/webhook handling, suppression lists, dead-letter/alert policies for irrecoverable errors, DKIM/SPF/DMARC, spam compliance and retention policies, multi-host/region recovery, and a tested backup/restore plan. Avoid announcing guaranteed instant delivery or exactly-once SMTP messaging.
