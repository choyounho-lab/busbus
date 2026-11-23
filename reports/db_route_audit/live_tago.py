"""Fetch complete TAGO city route listings and match database route IDs."""
import csv
import json
import sys
import time
from collections import Counter
from datetime import datetime
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import unquote, urlencode
from urllib.request import urlopen

OUT = Path(__file__).resolve().parent
BASE = "https://apis.data.go.kr/1613000/BusRouteInfoInqireService/"


def fetch(method, params, key):
    for attempt in range(3):
        try:
            with urlopen(BASE + method + "?" + urlencode(dict(params, serviceKey=key, _type="json")), timeout=15) as response:
                data = json.load(response)["response"]
            code = str(data["header"]["resultCode"])
            if code in {"00", "0", "03", "3"}:
                body = data.get("body") or {}
                items = (body.get("items") or {}).get("item") or []
                return (items if isinstance(items, list) else [items]), int(body.get("totalCount", 0)), None
            return [], 0, "service_" + code
        except HTTPError as exc:
            if exc.code in {400, 401, 403, 429} or attempt == 2:
                return [], 0, f"http_{exc.code}"
        except (URLError, TimeoutError, ValueError, TypeError, KeyError, AttributeError) as exc:
            if attempt == 2:
                return [], 0, type(exc).__name__
        time.sleep(attempt + 1)


def route_id(value):
    value = str(value).strip()
    return ("GGB" + value) if value.isdecimal() else value


def main():
    key = unquote(sys.stdin.readline().strip())
    if not key:
        sys.exit("Missing TAGO API key")
    with (OUT / "csv_comparison.csv").open(encoding="utf-8-sig") as file:
        rows = [r for r in csv.DictReader(file) if r["platform"] == "T"]
    selected_codes = {code for row in rows for code in row["csv_city_code"].split(" | ") if code}
    cities, _, error = fetch("getCtyCodeList", {}, key)
    if error:
        sys.exit("TAGO city list: " + error)
    selected_codes.update(str(city["citycode"]) for city in cities if str(city["citycode"]).startswith("31"))
    routes, failures = {}, {}
    for code in sorted(selected_codes):
        page, collected = 1, 0
        while True:
            batch, total, error = fetch("getRouteNoList", {"cityCode": code, "numOfRows": 1000, "pageNo": page}, key)
            if error or (not batch and collected < total):
                failures[code] = error or "incomplete_page"
                break
            for item in batch:
                routes[route_id(item["routeid"])] = {**item, "city_code": code}
            collected += len(batch)
            if collected >= total:
                break
            page += 1
        print("TAGO", code, collected, failures.get(code, "ok"), flush=True)
    counts = Counter()
    with (OUT / "live_T.jsonl").open("w") as out:
        for row in rows:
            rid = route_id(row["rte_id"])
            match = routes.get(rid)
            status = "matched_route_id" if match else "lookup_incomplete" if failures else "not_in_queried_city_lists"
            counts[bool(row["gbn_nm"]), status] += 1
            out.write(json.dumps({**row, "status": status, "match": match, "city_errors": failures, "checked_at": datetime.now().astimezone().isoformat()}, ensure_ascii=False) + "\n")
    print(counts, flush=True)


if __name__ == "__main__":
    main()
