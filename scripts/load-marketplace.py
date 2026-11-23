#!/usr/bin/env python3
"""Bounded, read-only, repeatable HTTP concurrency benchmark; Python stdlib only.

This benchmark never mutates users, inventory, orders, or payments.
"""
import argparse
import concurrent.futures
import json
import math
import statistics
import time
import urllib.error
import urllib.request
from collections import Counter
from pathlib import Path

PATHS = (
    "/api/store/catalog", "/api/store/catalog", "/api/store/catalog",
    "/api/store/categories",
    "/api/search/products?q=bluetooth&size=20",
    "/api/search/products?q=blue%20teuth&size=20",
    "/api/search/products?q=shoes&size=20",
    "/api/search/products?q=toys&size=20",
)

def percentile(values, quantile):
    return values[min(len(values)-1, max(0, math.ceil(quantile*len(values))-1))]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--requests", type=int, default=600)
    parser.add_argument("--concurrency", type=int, default=24)
    parser.add_argument("--timeout", type=float, default=15)
    parser.add_argument("--p95-ms", type=float, default=3500)
    parser.add_argument("--max-error-rate", type=float, default=0.01)
    parser.add_argument("--json-out", default="")
    args = parser.parse_args()
    if not 1 <= args.concurrency <= 250 or not 1 <= args.requests <= 100000:
        parser.error("concurrency must be 1..250; requests must be 1..100000")
    base = args.base_url.rstrip("/")
    for path in (PATHS[0], PATHS[4]):
        try:
            with urllib.request.urlopen(base + path, timeout=args.timeout) as response:
                if response.status != 200: raise RuntimeError("HTTP warmup failure")
                response.read()
        except Exception as exc:
            parser.error("warmup failed: "+type(exc).__name__)
    def hit(i):
        path = PATHS[i % len(PATHS)]
        started = time.perf_counter()
        try:
            with urllib.request.urlopen(base+path, timeout=args.timeout) as response:
                body=response.read()
                if response.status != 200 or not body:
                    return False,(time.perf_counter()-started)*1000,"HTTP "+str(response.status)
                return True,(time.perf_counter()-started)*1000,"OK"
        except urllib.error.HTTPError as exc:
            return False,(time.perf_counter()-started)*1000,"HTTP "+str(exc.code)
        except Exception as exc:
            return False,(time.perf_counter()-started)*1000,type(exc).__name__
    started=time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        results=list(executor.map(hit,range(args.requests)))
    duration=max(time.perf_counter()-started,0.001)
    latencies=sorted(row[1] for row in results)
    errors=Counter(row[2] for row in results if not row[0])
    successes=sum(row[0] for row in results)
    failure_rate=(args.requests-successes)/args.requests
    summary={
        "requests":args.requests, "concurrency":args.concurrency,
        "successes":successes, "failures":args.requests-successes,
        "failure_rate":round(failure_rate,5),
        "elapsed_seconds":round(duration,2),
        "throughput_rps":round(args.requests/duration,2),
        "latency_ms":{
            "p50":round(percentile(latencies,0.50),2),
            "p95":round(percentile(latencies,0.95),2),
            "p99":round(percentile(latencies,0.99),2),
            "max":round(max(latencies),2),
            "average":round(statistics.mean(latencies),2)
        },
        "errors":dict(errors),
        "thresholds":{"p95_ms":args.p95_ms,"max_error_rate":args.max_error_rate}
    }
    summary["passed"]=summary["latency_ms"]["p95"]<=args.p95_ms and failure_rate<=args.max_error_rate
    print(json.dumps(summary,indent=2))
    if args.json_out: Path(args.json_out).write_text(json.dumps(summary,indent=2)+"\n")
    return 0 if summary["passed"] else 1

if __name__=="__main__":
    raise SystemExit(main())
