# NOTHING Marketplace: offers, coupons and spend tiers

Added to branch `feat/nothing-marketplace-springboot`.

## Buyer experience
- Current active promotions on the homepage under **BETTER DEALS**.
- Coupon entry and removal in the bag, with server-calculated savings and payable total.
- Spend-range eligibility: minimum and optional maximum *pre-coupon* cart subtotal.
- Flat INR and percentage discounts with optional maximum saving cap.
- Time-limited coupons, total redemption caps, per-customer redemption limits, single offer per order. If no code selected, the best eligible automatically applied offer is selected (no promo stacking).
- Coupons persist on signed-in customer carts in PostgreSQL, across browser sessions and all three Spring Boot replicas.
- Both COD and Stripe-backed payments use the **same server-side pricing**. Stripe receives a final payable amount; on promotional orders it appears as one summary line on Stripe Checkout, while PostgreSQL retains each actual line item. Signed Stripe webhooks revalidate the expected amount and exact basket/offer state before confirming an order.
- Orders record the pre-discount subtotal, applied campaign code, savings and final payable amount. The customer order history shows offer savings.

## Admin experience
Log in with `ROLE_ADMIN` and select **OFFERS** in the header. Admins can create code campaigns, select FLAT/PERCENT, set cart min/max, saving cap, expiration days, total/per-buyer limit, and automatic eligibility. Existing campaigns may be paused/activated without deleting historical order redemption evidence.

Admin API:
- `GET /api/admin/offers`
- `POST /api/admin/offers`
- `PUT /api/admin/offers/{id}/active` with `{"active":false}`

Customer/public API:
- `GET /api/store/offers` (public)
- `GET /api/customer/cart/quote` (logged in)
- `PUT /api/customer/cart/coupon` with `{"code":"SAVE10"}` (logged in)
- `DELETE /api/customer/cart/coupon` (logged in)

## Optional demo campaigns
In LOCAL Docker profiles, `SEED_OFFERS=true` provides three real DB records once:
- `WELCOME100`: ₹100 off an order of ₹999 or more; one use/customer.
- `SAVE10`: 10% off a ₹1,999–₹5,999 order, capped at ₹400; two uses/customer.
- `AUTOSAVE5`: automatic 5% on ₹1,500+, saving capped at ₹250.

They are demonstrational offers, not promises that external brands or financial institutions will honor these codes. They have finite expiry and use counts. On Aiven profiles seeding is OFF; add real campaigns through the admin interface.

## Data consistency and safety
`marketplace_offers` stores campaign rules and redemption totals; `carts.coupon_code` stores a selected campaign; `orders.offer_code`, `orders.order_subtotal`, and `orders.discount_amount` store the purchase snapshot. Flyway V3 migrates existing PostgreSQL databases without erasing orders.

Coupon eligibility and usage counts are checked with database locks **inside the same order transaction that reserves stock and writes the outbox event**. The selected coupon is revalidated if the cart changes; an invalid selected code fails checkout rather than silently charging full price. Stripe webhooks are signed and idempotent. A coupon that expires or reaches its usage limit after an external Stripe payment starts can create a **manual reconciliation** requirement; implementing explicit expiring reservations and automatic refunds is still recommended before production deployment.

## Still not included
Cashback wallets, credit balances, bank-card offer BIN eligibility, gift cards, vouchers from external partners, free-shipping calculations, automatic refunds, coupon stacking, regional tax engines or affiliate rebates. These need their own financial/settlement rules and provider agreements.

Do not treat the demo or automated tests as real Stripe settlement or production financial certification.
