#!/bin/bash
set -euo pipefail
case "${DATABASE_URL:-}" in
  postgres://*|postgresql://*)
    db_location="${DATABASE_URL#*://}"
    db_host_path="${db_location#*@}"
    export DATABASE_URL="jdbc:postgresql://${db_host_path}"
    ;;
esac
: "${DATABASE_URL:?DATABASE_URL is required}"
export PORT="${PORT:-10000}"
export SERVER_PORT=8080
sed -e "s/{{PORT}}/${PORT}/g" -e "s/{{SERVER_PORT}}/${SERVER_PORT}/g" /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf
nginx -t
java -Dserver.port=8080 -jar /app/app.jar &
java_pid=$!
nginx -g 'daemon off;' &
nginx_pid=$!
trap 'kill -TERM "$java_pid" "$nginx_pid" 2>/dev/null || true; wait || true' EXIT
trap 'exit 143' TERM
trap 'exit 130' INT
wait -n "$java_pid" "$nginx_pid"
