#!/usr/bin/env bash
# Intentionally stops local services: NEVER run against a live customer environment.
# Explicit opt-in only for throwaway CI/local stacks.
set -eo pipefail
if [[ "${CHAOS_ACK:-}" != "ci-disposable" ]]; then
 echo "REFUSED: set CHAOS_ACK=ci-disposable ONLY for a disposable stack."
 exit 2
fi
compose_cmd(){ docker compose -f backend/compose.yml --env-file backend/.env "$@"; }
sql(){ compose_cmd exec -T postgres psql -v ON_ERROR_STOP=1 -U nothing -d nothing -tAc "$1"; }
recover(){ compose_cmd start kafka opensearch rabbitmq app >/dev/null 2>&1 || true; }
trap recover EXIT
BASE="${MARKET_URL:-http://localhost:8000}"
echo "[CHAOS 1/4] Kafka outage: retain DB outbox row while broker unavailable"
PRODUCT_ID="$(sql "select product_id from products where quantity>0 order by product_id limit 1" | tr -d '\r\n ')"
[[ "$PRODUCT_ID" =~ ^[0-9]+$ ]] || { echo "FAIL: No test products"; exit 1; }
EVENT_ID="$(python3 -c 'import uuid;print(uuid.uuid4())')"
compose_cmd stop kafka
sql "insert into nothing_outbox(id,topic,event_key,payload) values ('$EVENT_ID'::uuid,'nothing.events','product-$PRODUCT_ID',jsonb_build_object('eventId','$EVENT_ID','type','PRODUCT_CHANGED','productId',$PRODUCT_ID))" >/dev/null
[[ "$(sql "select count(*) from nothing_outbox where id='$EVENT_ID'::uuid and published_at is null" | tr -d '\r\n ')" == 1 ]] || { echo "FAIL: Outbox event was lost"; exit 1; }
curl -fsS --max-time 15 "$BASE/api/store/catalog" >/dev/null || { echo "FAIL: DB catalog unavailable during Kafka outage";exit 1; }
echo "[CHAOS 2/4] Kafka broker restart: publish and consume retained event"
compose_cmd start kafka
RECOVERED=0
for attempt in $(seq 1 85); do
 PUBLISHED="$(sql "select count(*) from nothing_outbox where id='$EVENT_ID'::uuid and published_at is not null" | tr -d '\r\n ')"
 CONSUMED="$(sql "select count(*) from nothing_consumed where event_id='$EVENT_ID'::uuid" | tr -d '\r\n ')"
 if [[ "$PUBLISHED" == 1 && "$CONSUMED" == 1 ]]; then RECOVERED=1;break;fi
 sleep 3
done
[[ "$RECOVERED" == 1 ]] || { echo "FAIL: Kafka replay timeout"; exit 1; }
echo "PASS: one durable event replayed, exactly one inbox record"
echo "[CHAOS 3/4] Search outage: database catalog survives, search returns after restart"
compose_cmd stop opensearch
curl -fsS --max-time 15 "$BASE/api/store/catalog" >/dev/null
compose_cmd start opensearch
RECOVERED=0
for attempt in $(seq 1 75); do
 if curl -fsSG --max-time 12 "$BASE/api/search/products" --data-urlencode 'q=PLAYLAB'  | python3 -c 'import json,sys; assert json.load(sys.stdin).get("hits",{}).get("hits")' >/dev/null 2>&1; then RECOVERED=1;break;fi
 sleep 3
done
[[ "$RECOVERED" == 1 ]] || { echo "FAIL: Search did not recover"; exit 1; }
echo "[CHAOS 4/4] RabbitMQ & app restart: PostgreSQL orders and catalog persist"
compose_cmd stop rabbitmq
curl -fsS --max-time 15 "$BASE/api/store/catalog" >/dev/null
compose_cmd start rabbitmq
compose_cmd stop app
compose_cmd start app
RECOVERED=0
for attempt in $(seq 1 70); do
 if curl -fsS --max-time 12 "$BASE/api/store/catalog" >/dev/null 2>&1; then RECOVERED=1;break;fi
 sleep 3
done
[[ "$RECOVERED" == 1 ]] || { echo "FAIL: App startup failed"; exit 1; }
[[ "$(sql "select count(*) from nothing_consumed where event_id='$EVENT_ID'::uuid" | tr -d '\r\n ')" == 1 ]]
echo "ALL DISPOSABLE FAULT-RECOVERY TESTS PASSED"
