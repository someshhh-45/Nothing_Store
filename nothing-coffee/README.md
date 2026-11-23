# NOTHING MARKET — editorial storefront for Spring Boot

This is a premium responsive multiproduct storefront, designed with the visual language of the Nothing Energy project, but connected to the **Java Spring Boot `backend` stack**, not the separate Python/FastAPI branch.

## Experience

- Dark oversized editorial hero, full-bleed category photography and custom Nothing-inspired typography.
- Dynamic grid covering Fashion, Toys & Games, Electronics/Tech, Gadgets, Home & Living, Grocery and Beauty.
- Search and category/city/price filtering via OpenSearch; dynamic client-side brand refinement on the server-indexed results; sorting and quick-view dialogs.
- Login, account, PostgreSQL-backed carts, addresses, cash-on-delivery checkout, order history, GSTIN retailer onboarding/admin approval, and optional signed-webhook Stripe Checkout are preserved from the Spring Boot backend.
- Accessible labels, mobile layout, keyboard-focus styles, alt text, hover/focus product detail, reduced-motion handling and product-image fallbacks.

Launch via `docker compose -f backend/compose.yml --env-file backend/.env up -d --build` **from the repository root**. Open http://localhost:8000. Do not open `index.html` as a `file://` page; same-origin `/api` calls require Nginx.

Images are representative and not photographs of authenticated products; the sample brands are fictional demo names. See [photo credits](PHOTO_CREDITS.md), [marketplace setup](../docs/README_MARKETPLACE.md), and the root [README](../README.md).