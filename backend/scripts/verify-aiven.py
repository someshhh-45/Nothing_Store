#!/usr/bin/env python3
"""Read-only Aiven live integration check (requires real private credentials).

Run after docker compose -f backend/compose.aiven.yml --env-file backend/.env.aiven up -d --build.
This checks actual PostgreSQL, Kafka SASL_SSL, OpenSearch HTTPS and RabbitMQ
through Spring Boot Actuator and exercises catalog/search. No secret output.
"""
import argparse
import json
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlparse,parse_qs

def dotenv(path):
    out={}
    for line in Path(path).read_text().splitlines():
        line=line.strip()
        if not line or line.startswith("#") or "=" not in line:continue
        k,v=line.split("=",1)
        out[k.strip()]=v.strip().strip("'")
    return out

def validate(env):
    required=("AIVEN_PG_JDBC_URL","AIVEN_PG_USER","AIVEN_PG_PASSWORD",
        "AIVEN_KAFKA_BOOTSTRAP","AIVEN_KAFKA_SASL_JAAS_CONFIG",
        "AIVEN_OPENSEARCH_URL","AIVEN_OPENSEARCH_USER","AIVEN_OPENSEARCH_PASSWORD")
    for key in required:
        v=env.get(key,"")
        if not v or any(marker in v.upper() for marker in ("REPLACE_ME","YOUR-AIVEN","EXAMPLE.INVALID")):
            raise RuntimeError("Configure real private value for "+key)
    pg=env["AIVEN_PG_JDBC_URL"]
    if not pg.startswith("jdbc:postgresql://"):raise RuntimeError("AIVEN_PG_JDBC_URL must be PostgreSQL JDBC")
    mode=parse_qs(urlparse(pg[5:]).query).get("sslmode",[""])[0]
    if mode not in ("require","verify-ca","verify-full"):
        raise RuntimeError("Aiven Postgres requires TLS sslmode=require or stronger")
    if not env["AIVEN_OPENSEARCH_URL"].startswith("https://"):
        raise RuntimeError("Aiven OpenSearch must use HTTPS")
    if "ScramLoginModule" not in env["AIVEN_KAFKA_SASL_JAAS_CONFIG"]:
        raise RuntimeError("Aiven Kafka needs SCRAM JAAS configuration")
    for key in ("AIVEN_PG_JDBC_URL","AIVEN_KAFKA_BOOTSTRAP","AIVEN_OPENSEARCH_URL"):
        if "localhost" in env[key] or "127.0.0.1" in env[key]:
            raise RuntimeError("Aiven endpoint cannot be local: "+key)

def get(url):
    try:
        with urllib.request.urlopen(url,timeout=12) as response:return json.load(response)
    except urllib.error.HTTPError as exc:raise RuntimeError("HTTP "+str(exc.code)) from None
    except Exception as exc:raise RuntimeError("Unreachable ("+type(exc).__name__+")") from None

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file",default="backend/.env.aiven")
    parser.add_argument("--metrics-port",type=int)
    parser.add_argument("--app-port",type=int)
    args=parser.parse_args()
    try:
        env=dotenv(args.env_file)
        validate(env)
    except (OSError,RuntimeError) as exc:
        print("FAIL: Aiven settings:",exc)
        return 2
    compose=["docker","compose","-f","backend/compose.aiven.yml","--env-file",args.env_file]
    if subprocess.run(compose+["config","--quiet"],capture_output=True).returncode:
        print("FAIL: invalid Aiven Compose configuration")
        return 2
    if not subprocess.run(compose+["ps","-q","app"],capture_output=True,text=True).stdout.strip():
        print("FAIL: Aiven application not running; start its Compose profile first")
        return 2
    metrics=args.metrics_port or int(env.get("APP_METRICS_PORT","8091"))
    port=args.app_port or int(env.get("APP_HTTP_PORT","8090"))
    base="http://127.0.0.1:"+str(metrics)+"/actuator/health"
    failed=False
    for component in ("db","kafkaBroker","searchCluster","rabbit"):
        try:
            if get(base+"/"+component).get("status")!="UP":raise RuntimeError("DOWN")
            print("PASS: live "+component+" UP")
        except RuntimeError as exc:
            print("FAIL: "+component+": "+str(exc))
            failed=True
    for label,path in (("DB catalog","/api/store/catalog"),("indexed search","/api/search/products?q=bluetooth")):
        try:
            if not get("http://127.0.0.1:"+str(port)+path):raise RuntimeError("empty response")
            print("PASS: "+label)
        except RuntimeError as exc:
            print("FAIL: "+label+": "+str(exc))
            failed=True
    print("LIVE AIVEN INTEGRATION:", "FAIL" if failed else "PASS")
    return 1 if failed else 0

if __name__=="__main__":sys.exit(main())
