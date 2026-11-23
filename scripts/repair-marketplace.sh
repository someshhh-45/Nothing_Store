#!/usr/bin/env bash
# Safe recovery for a locally empty or stale Nothing Market demo catalog.
# DOES NOT delete PostgreSQL, OpenSearch or other named volumes.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f backend/.env ]]; then
  echo "Missing backend/.env. Copy backend/.env.example and set secrets first."
  exit 1
fi
compose(){ docker compose -f backend/compose.yml --env-file backend/.env "$@"; }
check_catalog(){
  curl -fsS --max-time 15 http://localhost:8000/api/store/catalog |
  python3 -c '
import json,sys
data=json.load(sys.stdin)
assert isinstance(data,list), "Catalog response must be an array"
categories={p.get("category") for p in data}
brands={p.get("brand") for p in data if p.get("brand")}
variants=[p for p in data if p.get("variantLabel")]
assert len(data)>=100, f"Found {len(data)} products; expected at least 100 on demo database"
assert {"Fashion","Electronics","Gadgets","Toys & Games"}<=categories,categories
assert len(brands)>=20, f"Found {len(brands)} brand labels"
assert len(variants)>=20, f"Found {len(variants)} sized products"
print("PASS:",len(data),"products,",len(brands),"brands,",len(variants),"size SKUs")
'
}
echo "Checking Nothing Market catalog..."
if check_catalog; then
  echo "Catalog already healthy; no database changes needed."
  exit 0
fi
echo "Catalog incomplete or Spring Boot not ready; safely recreating API and frontend."
compose up -d --build --force-recreate app frontend
for attempt in $(seq 1 45); do
  if check_catalog 2>/dev/null; then
    echo "Catalog recovered; size SKUs are available."
    exit 0
  fi
  echo "Waiting for demo seed on attempt $attempt/45..."
  sleep 3
done
echo "Still incomplete. Verify SEED_RETAIL_CATALOG=true and the correct Compose project."
compose ps
compose logs --tail=110 app postgres
exit 1
