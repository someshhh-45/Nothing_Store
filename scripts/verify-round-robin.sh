#!/usr/bin/env bash
# Verify real round-robin distribution, cross-replica JWT auth and app failover.
# Only targets disposable local HA deployments.
set -euo pipefail
COMPOSE=(docker compose -f backend/compose.ha.yml --env-file backend/.env)
BASE="${MARKET_URL:-http://127.0.0.1:8000}"
EXPECTED="${HA_EXPECT_REPLICAS:-3}"
CI_USER="${HA_TEST_USER:-ci-ha-admin}"
CI_PASS="${HA_TEST_PASSWORD:-CI_HA_ADMIN_Only_2026_Strong!}"
stopped=""
cookie="$(mktemp)"
cleanup() {
  if [[ -n "$stopped" ]]; then docker start "$stopped" >/dev/null 2>&1 || true; fi
  rm -f "$cookie"
}
trap cleanup EXIT
chmod 600 "$cookie"
echo "[HA 1/5] Nginx config and app replica count"
"${COMPOSE[@]}" exec -T frontend nginx -t
for attempt in $(seq 1 60); do
  count="$("${COMPOSE[@]}" ps -q app | grep -c . || true)"
  if [[ "$count" -ge "$EXPECTED" ]];then break;fi
  sleep 2
done
[[ "$count" -ge "$EXPECTED" ]] || { echo "FAIL: expected $EXPECTED replicas, got $count";exit 1; }
echo "PASS: $count running Spring Boot replicas"
echo "[HA 2/5] Round-robin distribution across distinct application addresses"
found=0
for attempt in $(seq 1 30); do
  for i in $(seq 1 24); do
    curl -fsS --max-time 20 -o /dev/null "$BASE/api/store/categories"
  done
  log="$("${COMPOSE[@]}" logs --no-color --tail=1600 frontend 2>/dev/null || true)"
  found="$(printf '%s\n' "$log" | grep -oE 'upstream=([0-9]{1,3}\.){3}[0-9]{1,3}:8080' | sort -u | wc -l | tr -d ' ' || true)"
  if [[ "$found" -ge "$EXPECTED" ]]; then break;fi
  sleep 2
done
[[ "$found" -ge "$EXPECTED" ]] || { echo "FAIL: requests reached only $found distinct backend IPs";exit 1; }
echo "PASS: observed $found unique app upstreams handling HTTP requests"
echo "[HA 3/5] One signed JWT works on every replica without sticky sessions"
login="$(HA_USER="$CI_USER" HA_PASS="$CI_PASS" python3 -c 'import json,os;print(json.dumps({"username":os.environ["HA_USER"],"password":os.environ["HA_PASS"]}))')"
curl -fsS --max-time 20 -c "$cookie" -H 'Content-Type: application/json' -d "$login" "$BASE/api/auth/signin" >/dev/null
for i in $(seq 1 36); do
  curl -fsS --max-time 20 -b "$cookie" "$BASE/api/auth/user" |
    python3 -c 'import json,sys;assert json.load(sys.stdin)["username"]=="ci-ha-admin"'
done
echo "PASS: cross-replica auth using a single JWT cookie"
echo "[HA 4/5] One Spring Boot container fails; requests continue through healthy peers"
stopped="$("${COMPOSE[@]}" ps -q app | head -n 1)"
[[ -n "$stopped" ]] || { echo "FAIL: missing app container";exit 1; }
docker stop -t 20 "$stopped" >/dev/null
sleep 7
for i in $(seq 1 48); do
  curl -fsS --retry 0 --max-time 20 -o /dev/null "$BASE/api/store/categories"
done
for i in $(seq 1 12); do
  curl -fsS --max-time 20 -b "$cookie" "$BASE/api/auth/user" >/dev/null
done
echo "PASS: healthy replicas served requests after one backend stopped"
echo "[HA 5/5] Restart failed replica and wait for healthy state"
docker start "$stopped" >/dev/null
restored="$stopped"
stopped=""
healthy=0
for attempt in $(seq 1 65); do
  if [[ "$(docker inspect --format '{{.State.Health.Status}}' "$restored" 2>/dev/null || true)" == "healthy" ]];then
    healthy=1
    break
  fi
  sleep 3
done
[[ "$healthy" == 1 ]] || { echo "FAIL: restarted backend did not become healthy";exit 1; }
echo "ALL HA ROUND-ROBIN / JWT / FAILOVER CHECKS PASSED"
