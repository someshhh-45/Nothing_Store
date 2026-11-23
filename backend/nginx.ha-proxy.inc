# Shared settings for all /api/ locations. Only idempotent requests are
# retried after an upstream failure (NGINX default for non-idempotent methods).
proxy_pass http://nothing_app;
proxy_http_version 1.1;
proxy_set_header Connection "";
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Host $host;
proxy_connect_timeout 3s;
proxy_send_timeout 35s;
proxy_read_timeout 35s;
proxy_next_upstream error timeout http_502 http_503 http_504;
proxy_next_upstream_tries 3;
proxy_intercept_errors off;
