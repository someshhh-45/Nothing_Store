# NOTHING Market: authentication and customer operations

Target branch: `feat/nothing-marketplace-springboot` in `PunithNS-pixel/nothing`.
These are implementation notes for the Java/Spring Boot marketplace, not the FastAPI `feat/nothing` project.

## Authentication components

| Component | Role |
|---|---|
| `nothing-coffee/app.js` | Same-origin `/api` browser requests, account dialog and signed-in session check |
| `backend/nginx.conf` | Sends storefront `/api/*` requests to Spring Boot |
| `Controller/AuthController.java` | `/api/auth/signup`, `verify-email`, `signin`, `user`, `signout`, `resend-code` |
| `Security/Services/EmailSignupService.java` | SMTP-backed six-digit mailbox OTP signup, resend delay, expiration, attempt limit |
| `Service/ServiceImpl/AuthServiceImpl.java` | Spring AuthenticationManager login, JWT cookie issuance, user/role response |
| `Security/Services/UserDetailsServiceImpl.java` | Looks up existing users in PostgreSQL |
| `Security/WebSecurityConfig.java` | BCrypt password encoder, stateless security filter chain, role rules |
| `Security/JWT/AuthTokenFilter.java` | Extracts cookie first or Authorization Bearer token, validates signature/expiry, sets SecurityContext |
| `Security/JWT/JwtUtils.java` | JWT signing/validation and cookie creation/deletion |
| `Util/AuthUtil.java` | Extracts authenticated account identity for orders, carts, addresses and ownership checks |

### New-account signup flow

1. The shopper sends `POST /api/auth/signup` with username, email and password.
2. The server validates the input and checks usernames/emails against registered and pending accounts.
3. It stores a **BCrypt password hash** and a **separate BCrypt hash of a random six-digit OTP** in `pending_signups`. No user account is created yet.
4. A real SMTP provider must send the OTP. **Without SMTP, signup fails with 503.** The code is never included in the API response or logged. A code expires in ten minutes; attempts are limited to five, and new codes are rate-limited to one per minute per pending account.
5. The shopper enters the code in the verification dialog. `POST /api/auth/verify-email` checks it; on success, the server moves the account to `users` with `ROLE_USER` and deletes the pending registration.
6. Only then does the browser call `POST /api/auth/signin`. Old pre-existing users remain active; this change does not retroactively verify their previous email addresses.

An address merely passing email format validation is **not** mailbox proof. SMTP delivery and possession of the code provide that proof. Email OTP is **not** phone/SMS OTP; integrating a real SMS provider still requires separate work.

### Existing-account login and token flow

```text
Browser  POST /api/auth/signin {username,password}
   |        Nginx same-origin /api proxy
   v
AuthController -> AuthServiceImpl
   |     Spring AuthenticationManager -> DaoAuthenticationProvider
   |       -> UserDetailsServiceImpl -> PostgreSQL users and roles
   |       -> BCrypt password comparison
   v
JwtUtils signs a short JSON Web Token
   |     Set-Cookie: nothingSession=<jwt>; Path=/api;
   |                 HttpOnly; SameSite=Lax; Max-Age=86400
   |                 Secure (ONLY when JWT_COOKIE_SECURE=true)
   v
Browser automatically sends the cookie to /api
   -> AuthTokenFilter validates token and loads CURRENT roles
   -> Spring Security authorizes the specific route
   -> Controller uses AuthUtil to enforce data ownership
```

The token contains the **username as its subject**, issuance date, and expiration timestamp; it does not contain the password. Signing material is read from the Base64-encoded `JWT_SECRET` environmental secret and must never be committed. `JWT_EXPIRATION_MS` configures token validity; the browser cookie is set for 24 hours. A caller may alternatively send `Authorization: Bearer <token>`. Logout deletes the cookie, but does **not** revoke an already-extracted bearer token. JWTs should be short-lived with revocation/rotation before production.

`GET /api/auth/user` returns 401 when not logged in; `GET /api/auth/username` can return an empty string and is **not a session check**. The browser correctly uses `/api/auth/user`. Public signup cannot assign elevated roles even if `role` is supplied in the JSON body. Seller roles still require trusted provisioning; GSTIN wholesale-buyer approval is **not** seller authorization.

### Risk and security checklist

- **Still open:** Spring CSRF protection is disabled for this cookie-authenticated API. HTTPS, CSRF tokens or reliable origin protections, token revocation, IP-based rate limits, account recovery, audit logging, stricter authorization testing and penetration testing are required before public launch.
- The legacy public category-creation/update URLs now require `ROLE_ADMIN`; product image upload is admin-only until seller-scoped ownership validation is implemented.
- Cookies must be `Secure` on HTTPS; configure `JWT_COOKIE_SECURE=true` in production.
- Use unique strong DB, admin, JWT, Aiven, Stripe, SMTP and RabbitMQ credentials in private env/config or a secret manager, never frontend JavaScript or Git.
- Stripe success redirects are not payment evidence; the signed webhook remains authoritative.

## Operations API and UI added

| Feature | Routes and behavior |
|---|---|
| Wishlist | `GET /api/customer/wishlist`, `PUT/DELETE /api/customer/wishlist/{productId}`. Stored per authenticated user in PostgreSQL; "SAVED ♡" opens the UI. |
| Feedback | `POST /api/customer/products/{id}/reviews`, `GET /api/store/products/{id}/reviews`. Ratings 1–5; one editable review per actual purchasing customer. |
| Recommendations | `GET /api/store/recommendations?productId=123`. Dynamic, in-stock category matches with same-brand preference; **not** personalized ML. |
| Returns | `POST /api/customer/orders/{id}/requests` with `type=RETURN` and reason. Allowed for a delivery with a recorded timestamp within 14 days, then admin review. Approval **does not** automatically refund or collect the product. |
| Rescheduling | Same endpoint with `type=RESCHEDULE`, reason and `requestedDate`. Requests accepted only before shipment, for a future date within 30 days; an admin approval updates the manually estimated date. |
| Request statuses | `GET /api/customer/order-requests`; admin queue `GET /api/admin/order-requests` and `PUT /api/admin/order-requests/{id}/review`. |
| Admin stock | `PUT /api/admin/products/{id}/stock`, `PUT /api/admin/products/{id}/discount`. Row-locked writes, limits and outbox events. |
| Seller stock | `PUT /api/seller/products/{id}/stock`, `PUT /api/seller/products/{id}/discount`. Seller must own the product. |
| Product creation | Existing `POST /api/admin/categories/{categoryId}/product` and seller variant; new inventory UI exposes them. |
| Browser address location | A voluntary "USE CURRENT LOCATION" browser geolocation button saves optional latitude/longitude on the delivery address. Full delivery address is still required. |
| Tracking | Existing `GET /api/customer/orders/{id}/tracking` shows real staff-entered stages from PostgreSQL; **no live rider/courier GPS**. |

After-sales requests deliberately don't bypass status, payment or ownership rules. No SMS code, automated refunds, shipping-provider GPS stream or courier ETA is claimed.

## Email configuration and confirmation delivery

Add these to the untracked `backend/.env` (or `backend/.env.aiven`):

```dotenv
SMTP_HOST=smtp.your-provider.example
SMTP_PORT=587
SMTP_USER=YOUR_SMTP_ACCOUNT
SMTP_PASSWORD=YOUR_PROVIDER_APP_PASSWORD
SMTP_STARTTLS=true
EMAIL_FROM=no-reply@your-verified-domain.example
MAIL_NOTIFICATIONS_ENABLED=true
MAIL_HEALTH_ENABLED=true
```

Choose a real provider and verify the sender domain; the values shown above are placeholders. New signup always requires working SMTP. `MAIL_NOTIFICATIONS_ENABLED=true` starts the RabbitMQ email consumer that sends **actual order confirmation messages** for existing `ORDER_PLACED` outbox events. When disabled, the durable `nothing.notifications` queue holds messages. The Kafka outbox is committed in the same transaction as the order. Email delivery is *at least once*, not exactly once, and can be retried/duplicated after consumer failure. Configure retry limits, DLQ, idempotent delivery records and mailbox provider monitoring for production. No confirmation mail is sent unless real SMTP is configured and working.

## Windows PowerShell startup

```powershell
git fetch origin
git switch feat/nothing-marketplace-springboot
git pull --ff-only origin feat/nothing-marketplace-springboot

if (!(Test-Path backend/.env)) { Copy-Item backend/.env.example backend/.env }
notepad backend/.env
# Set strong LOCAL_DB_PASSWORD, DATABASE_PASSWORD, RABBITMQ_PASSWORD,
# JWT_SECRET, optional admin credentials and REAL SMTP credentials.

docker compose -f backend/compose.yml --env-file backend/.env config --quiet
docker compose -f backend/compose.yml --env-file backend/.env up -d --build
docker compose -f backend/compose.yml --env-file backend/.env ps
```

Open `http://localhost:8000`, not the direct Spring Boot API URL. A healthy unauthenticated API session check should yield HTTP 401. Test with new emails that you own; the signup flow needs real mailbox delivery. Do not use `docker compose down -v` on valuable accounts/orders. PostgreSQL schema additions rely on the current `HIBERNATE_DDL_AUTO=update` demo setting; prepare explicit reviewed migrations before production.

### Validation

- Java CI runs `mvn -B clean verify` against PostgreSQL and checks browser JavaScript and page element IDs.
- Full-stack CI boots PostgreSQL, Spring Boot, Kafka, RabbitMQ, OpenSearch and Nginx with disposable credentials and runs smoke checks.
- `EmailSignupVerificationTest`, `MarketplaceEngagementApiTest`, and `AfterSaleApiTest` cover important flows.
- Tests do **not** prove a real SMTP provider, live Stripe settlement, shipping courier integration, long-duration load/performance, or Aiven production credentials work. These require external end-to-end testing.

## Outstanding work

SMS OTP provider and delivery, fraud controls, seller onboarding/identity verification, return-label generation and refunds, genuine courier GPS/tracking-provider integration, tax/invoicing, privacy/consent policies, distributed performance/load tests, full CSRF hardening, backup/restore and provider-level SLA testing remain outstanding.
