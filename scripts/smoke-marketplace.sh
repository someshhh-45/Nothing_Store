#!/usr/bin/env bash
# Read-only local Spring Boot full-stack smoke checks. No user/order mutations.
set -euo pipefail
BASE="${MARKET_URL:-http://localhost:8000}"
API="${MARKET_API:-http://localhost:8090}"
COMPOSE=(docker compose -f backend/compose.yml --env-file backend/.env)
printf '\n[1/8] Containers\n'
"${COMPOSE[@]}" ps
printf '\n[2/8] Nginx storefront and Spring Boot catalog (>= 100 demo products)\n'
for attempt in $(seq 1 50); do
  if curl -fsS --max-time 7 "$BASE/api/store/catalog" | python3 -c '
import json,sys
rows=json.load(sys.stdin)
assert len(rows) >= 100, f"expected >=100 items, got {len(rows)}"
brands={x.get("brand") for x in rows if x.get("brand")}
assert len(brands)>=20, brands
variants=[p for p in rows if p.get("variantLabel")]
assert len(variants)>=20, f"Expected size-specific stock SKUs; found {len(variants)}"
assert any(p.get("sizeType")=="SHOE" and p.get("footLengthCm") for p in variants), "Shoe cm sizing missing"
categories={x["category"] for x in rows}
assert {"Fashion","Electronics","Gadgets","Toys & Games"}.issubset(categories),categories
assert all(x.get("image", "").startswith("https://") for x in rows if x.get("brand") != "NOTHING SELECT")
print("CATALOG PASS", len(rows),"stock-bearing SKUs,",len(brands),"brands,",len(variants),"size variants, categories:",sorted(categories))
' 2>/dev/null; then break; fi
  if [ "$attempt" -eq 50 ]; then
    echo 'FAIL: catalog not ready, expected 100+ products'; "${COMPOSE[@]}" logs --tail=100 app; exit 1
  fi
  sleep 4
done
printf '\n[3/8] HTML and JS served\n'
HTML_FILE="$(mktemp)"
JS_FILE="$(mktemp)"
trap 'rm -f "$HTML_FILE" "$JS_FILE"' EXIT
curl -fsS "$BASE/" -o "$HTML_FILE"
curl -fsS "$BASE/app.js" -o "$JS_FILE"
grep -q 'NOTHING MARKET' "$HTML_FILE"
grep -q 'brandFilter' "$JS_FILE"
printf 'STOREFRONT PASS\n'
printf '\n[4/8] OpenSearch indexing and brand search\n'
for attempt in $(seq 1 40); do
  if curl -fsSG --max-time 12 "$BASE/api/search/products" --data-urlencode q=PLAYLAB --data-urlencode size=100 | python3 -c '
import json,sys
result=json.load(sys.stdin)
hits=result.get("hits",{}).get("hits",[])
assert any("PLAYLAB" in h.get("_source",{}).get("productName","") for h in hits)
print("OPENSEARCH PASS",len(hits),"brand matches")
' 2>/dev/null; then break; fi
  if [ "$attempt" -eq 40 ]; then echo 'FAIL: PLAYLAB OpenSearch search';"${COMPOSE[@]}" logs --tail=100 opensearch app;exit 1;fi
  sleep 4
done
printf '\n[4b/8] Split-word fuzzy Bluetooth search regression\n'
for attempt in $(seq 1 12); do
  if curl -fsSG --max-time 15 "$BASE/api/search/products" --data-urlencode 'q=blue teuth' --data-urlencode size=100 | python3 -c '
import json,sys
result=json.load(sys.stdin)
hits=result.get("hits",{}).get("hits",[])
names=[h.get("_source",{}).get("productName","") for h in hits]
assert any("bluetooth" in n.lower() for n in names),names
print("TYPO-TOLERANT SEARCH PASS",names[:6])
' 2>/dev/null;then break;fi
  if [ "$attempt" -eq 12 ];then echo 'FAIL: OpenSearch split-word Bluetooth search';exit 1;fi
  sleep 3
done
printf '\n[5/8] Auth boundary\n'
code="$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/api/auth/user")"
[ "$code" = '401' ] || { echo "expected unauthenticated 401, got $code";exit 1; }
printf 'AUTH PASS 401 (signed out)\n'
printf '\n[6/8] RabbitMQ and Kafka broker readiness\n'
"${COMPOSE[@]}" exec -T rabbitmq rabbitmq-diagnostics -q ping
"${COMPOSE[@]}" exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list >/dev/null
printf 'MESSAGING BROKERS PASS\n'
printf '\n[7/8] Micrometer / monitoring\n'
curl -fsS http://localhost:3000/api/health >/dev/null
curl -fsS 'http://localhost:9090/api/v1/query?query=up' | python3 -c 'import json,sys; assert json.load(sys.stdin)["status"]=="success"'
# Health may expose only the dedicated management port.
curl -fsS http://localhost:8091/actuator/health >/dev/null
printf 'MONITORING PASS\n'
printf '\n[8/8] Optional Stripe safety\n'
curl -fsS "$BASE/api/checkout/stripe/config" | python3 -c 'import json,sys; data=json.load(sys.stdin); assert isinstance(data.get("enabled"),bool); print("STRIPE CONFIG PASS:", "configured; perform a real test-mode webhook checkout separately" if data["enabled"] else "disabled until Stripe keys are configured")'
printf '\nALL MULTICATEGORY MARKETPLACE SMOKE TESTS PASSED. Test-mode Stripe payments and live Aiven remain separately required.\n'