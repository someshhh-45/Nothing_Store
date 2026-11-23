# Google Cloud Console + Gmail API: order confirmations and signup emails

The NOTHING Marketplace backend supports `MAIL_PROVIDER=gmail-api` as an alternative to SMTP. Both transactional confirmation emails (COD / signed paid Stripe events) and new-account six-digit OTP emails use the same Google-authorized mailbox.

**Google Cloud Console is not itself an email delivery service.** You must enable the **Gmail API** in your Google Cloud project and authorize a Google account that has Gmail enabled, with the send-only `https://www.googleapis.com/auth/gmail.send` scope. Google OAuth user consent is required for a personal Gmail account; a Google Cloud service account alone cannot send from an arbitrary consumer Gmail mailbox. Gmail API send is subject to Google's quotas, policy and sender reputation.

## 1. Set up Google Cloud Console

1. Open <https://console.cloud.google.com/> and select/create the project you own.
2. Navigate to **APIs & Services → Library → Gmail API → Enable** (or [open the Gmail API page](https://console.cloud.google.com/apis/library/gmail.googleapis.com)).
3. Under **Google Auth Platform → Branding / Audience / Data Access**, configure the OAuth app. For a personal Gmail account, use **External** audience and add the sender account under **Test users** while testing. For a Google Workspace organization, Internal may be available.
4. Data access: grant only `https://www.googleapis.com/auth/gmail.send` (sending only, not reading your inbox).
5. Under **Google Auth Platform → Clients**, create an **OAuth client ID → Desktop app**. Download its **client_secret_….json** file to a private directory on your Mac (such as `~/Downloads`). **Do not commit or upload the downloaded JSON or any refresh token.**
6. The mailbox you authorize must be your `EMAIL_FROM`, or a verified Gmail send-as alias. This guide is for a single store-owned mailbox, not sending on behalf of each shopper.

Google documentation: [Gmail API Java quickstart](https://developers.google.com/workspace/gmail/api/quickstart/java), [Gmail sending](https://developers.google.com/workspace/gmail/api/guides/sending), [Desktop OAuth](https://developers.google.com/identity/protocols/oauth2/native-app).

## 2. Do one-time OAuth on your Mac

From repository root, with Python 3 installed:

```bash
git fetch origin
git switch feat/nothing-marketplace-springboot
git pull --ff-only origin feat/nothing-marketplace-springboot
test -f backend/.env || cp backend/.env.example backend/.env
python3 scripts/setup-gmail-oauth.py ~/Downloads/client_secret_YOUR-REAL-FILE.json
```

**Replace the JSON filename with the actual path** of the OAuth Desktop app credential downloaded from your Google Cloud project. The script starts a temporary loopback listener bound to **127.0.0.1**, opens your browser for Google consent (with CSRF state + PKCE), and exchanges the returned authorization code for a refresh token. It writes `MAIL_PROVIDER=gmail-api`, `MAIL_NOTIFICATIONS_ENABLED=true`, `EMAIL_FROM`, and the three `GMAIL_OAUTH_*` values into **ignored `backend/.env` with permissions 0600**. It does not print access/refresh tokens or transmit them to GitHub.

Only the sender Gmail account has to authorize. Store buyers **never** grant Google scopes to your backend.

Google Cloud projects using an **External** OAuth audience with publishing status **Testing** generally issue a refresh token that expires after **7 days** for Gmail scopes. Move the OAuth application through appropriate publishing/verification procedures for sustained use, or reauthorize when the refresh token expires. Google's [OAuth expiration policies](https://developers.google.com/identity/protocols/oauth2) apply.

## 3. Start existing local Docker stack

For your **existing standard** Docker deployment, preserving the same PostgreSQL volume:

```bash
docker compose -f backend/compose.yml --env-file backend/.env config --quiet
docker compose -f backend/compose.yml --env-file backend/.env up -d --build --force-recreate app
```

For the **existing HA deployment** if you're already using it:

```bash
docker compose -f backend/compose.ha.yml --env-file backend/.env config --quiet
docker compose -f backend/compose.ha.yml --env-file backend/.env up -d --build --scale app=3 --force-recreate app
```

**Choose only one deployment.** HA and standard Compose stacks use different named PostgreSQL volumes. Do not `down -v`, or use a new stack to look for existing order data. The same `MAIL_PROVIDER` / `GMAIL_OAUTH_*` values are forwarded by both Aiven Compose profiles as well, using their respective private env files.

## 4. Test a REAL email and status

1. Open <http://localhost:8000>, log in with a **verified** customer email, select a product and address, and place a COD order.
2. Check your recipient inbox **and spam folder**. The expected subject is `NOTHING Market | Order #<id> confirmed`. The HTML receipt contains items, quantities, final INR amount, applied offer, shipping address and correct COD language.
3. In customer **Orders → Confirmation Email Status**, see `PENDING → SENDING → SENT`. `SENT` means Google accepted the API request; this is **not** proof of final inbox delivery.
4. Signup OTPs are also sent by Gmail API. A bogus/unreachable address cannot complete registration unless someone receives its verification code.

For local diagnostic logs (no credential contents):

```bash
docker compose -f backend/compose.yml --env-file backend/.env logs --tail=100 app
```

Check the per-order row through a normal database session:

```sql
SELECT order_id, status, attempts, sent_at, last_error_code
FROM order_confirmation_emails ORDER BY order_id DESC LIMIT 10;
```

If `PENDING` with repeated attempts, verify Gmail API enabled, OAuth consent, refresh token not expired, the sender account, and Gmail quota/permissions. The app logs only generic exception names and status, **never OAuth tokens or Gmail response content**. Gmail API scope errors will not mark a message as SENT.

## Security, operational limits, and defaults

- `MAIL_PROVIDER=smtp` remains the default; existing authenticated SMTP delivery is preserved.
- No mail provider, OAuth secret, customer emails, OAuth JSON or token is committed to source. The `backend/.gitignore` pattern excludes `.env` and `.env.*` (except example templates).
- Gmail API OAuth is scoped to **one explicitly consenting sender mailbox** with `gmail.send`. Do not share refresh tokens, OAuth client secrets, or JSON downloads.
- The existing PostgreSQL email job and claim/retry path guards normal duplicate sends. An API request might succeed just before a process crashes, so **SMTP/Gmail API cannot guarantee exactly-once delivery**.
- The Google-hosted Gmail API, backend and outbound internet must be reachable, and Google sender limits apply. CI tests use HTTP mocks and **never call real Gmail**.
- Consumer Gmail or a Google Workspace mailbox is required for Gmail API. Cloud Storage/Cloud Run/IAM alone do not grant a right to send Gmail messages.
