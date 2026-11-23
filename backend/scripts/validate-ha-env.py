#!/usr/bin/env python3
"""Validate production HA prerequisites without printing credentials."""
import argparse
import base64
import binascii
import sys
from pathlib import Path
from urllib.parse import urlparse
from verify_aiven import dotenv,validate

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file",default="backend/.env.aiven")
    args=parser.parse_args()
    try:
        env=dotenv(args.env_file)
        validate(env)
        def need(key):
            value=env.get(key,"")
            if not value or "REPLACE" in value.upper() or "CHANGE_ME" in value.upper():
                raise RuntimeError("Missing or placeholder value: "+key)
            return value
        key=need("JWT_SECRET")
        try: raw=base64.b64decode(key,validate=True)
        except (ValueError,binascii.Error):
            raise RuntimeError("JWT_SECRET must be valid Base64") from None
        if len(raw)<32:raise RuntimeError("JWT_SECRET must encode at least 32 random bytes")
        url=urlparse(need("FRONTEND_URL"))
        if url.scheme!="https" or not url.hostname:
            raise RuntimeError("FRONTEND_URL must be a valid public https:// URL")
        for key in ("TLS_CERT_PATH","TLS_KEY_PATH","AIVEN_PG_CA_PATH"):
            if not Path(need(key)).is_file(): raise RuntimeError(key+" must point to an existing PEM file")
        if env.get("HIBERNATE_DDL_AUTO","validate").lower()!="validate":
            raise RuntimeError("Set HIBERNATE_DDL_AUTO=validate; migrate schema before deployment")
        if env.get("SEED_RETAIL_CATALOG","false").lower() not in ("false","0",""):
            raise RuntimeError("Disable demo catalog seeding on live replicas")
        for key in ("RABBITMQ_PASSWORD","GRAFANA_ADMIN_PASSWORD"):
            if len(need(key))<12: raise RuntimeError("Use a >=12-character secret: "+key)
        if env.get("STRIPE_SECRET_KEY") and not env.get("STRIPE_WEBHOOK_SECRET"):
            raise RuntimeError("Stripe requires webhook signature verification")
        for key in ("SMTP_HOST","SMTP_USER","SMTP_PASSWORD","EMAIL_FROM"):
            need(key)
    except (OSError,RuntimeError) as err:
        print("FAIL: HA environment preflight:",err)
        return 2
    print("PASS: HA configuration secrets, certificates, HTTPS, Aiven endpoints and migration-mode preflight")
    print("NOTE: this does not verify live Aiven connections, backups, Stripe or production safety.")
    return 0

if __name__=="__main__":
    sys.exit(main())
