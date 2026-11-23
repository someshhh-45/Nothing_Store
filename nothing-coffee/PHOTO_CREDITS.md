# NOTHING MARKET — photo sourcing and merchandising policy

**All listed products and brand labels in `catalog/marketplace-v2.tsv` are synthetic demo records.** Product images are curated *representative/lifestyle* photos and must not be presented as an accurate photograph of the item that will ship. They are not real merchant offers, price quotes, inventory records, sponsorships, or proof of authenticity. Replace every listing with verified merchant-supplied product data and license-cleared exact-SKU images before accepting public payments. The app exposes backend prices, quantities and cart/order logic but the catalog is for demonstration.

## Image sources

- Unsplash license: https://unsplash.com/license
- Unsplash commercial use explainer: https://help.unsplash.com/en/articles/2612315-can-i-use-unsplash-images-for-personal-or-commercial-projects
- Existing marketplace seed images are listed in `backend/src/main/java/com/eshop/Ecommerce/extension/RetailCatalogSeed.java`.
- The 56 new product image URLs appear per row in `backend/src/main/resources/catalog/marketplace-v2.tsv`.
- The four category editorial tiles and two hero photos are linked directly in `nothing-coffee/index.html`.
- Toy photo sources include publicly listed free Unsplash photos of building blocks, a teddy bear, a model car, a puzzle cube, a shape-sorting set and a model robot; example photo pages:
  - https://unsplash.com/photos/a-pile-of-colorful-building-blocks-scattered-on-a-surface-DmDTCNdGLw8
  - https://unsplash.com/photos/a-white-teddy-bear-sitting-on-a-red-background-HWdD11AKWVI
  - https://unsplash.com/photos/orange-and-blue-drift-remote-control-car-on-reflective-surface-2G9inL8M1VE
  - https://unsplash.com/photos/3-x-3-rubiks-cube-IgPIM02gtO4
  - https://unsplash.com/photos/wooden-shape-sorter-toy-with-colorful-blocks-E1shiIGT8Oo
  - https://unsplash.com/photos/a-toy-that-is-sitting-on-a-table-uRYUcRyWVwg

Some demo images share a category-level photograph, even when their product titles differ. Individual CDN links can change or stop loading; the UI shows a product-themed fallback illustration rather than a broken-image glyph. Links point to the CDN and are not packaged as downloaded binary images.

## Before real commerce

1. Source original, authorized product photographs and specifications from each seller or manufacturer.
2. Verify brand names, trademarks, model numbers, age warnings for toys, product safety, size/fit variants, and pricing.
3. Add a media-management service, optimized local object storage or CDN, alternative text, responsive srcset, and thumbnail generation.
4. Provide returns/shipping details, GST invoices, shipping eligibility and inventory synchronization by merchant/warehouse.
5. Record attribution/permissions as applicable and confirm rights for people, property and third-party trademarks in photographs.