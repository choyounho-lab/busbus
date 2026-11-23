"""Search the map sites in Chrome and retain candidates for each DB route."""
import csv
import json
import re
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path
from urllib.parse import quote

from selenium import webdriver
from selenium.common.exceptions import WebDriverException
from selenium.webdriver.chrome.service import Service

OUT = Path(__file__).resolve().parent
HOST = {"N": "https://map.naver.com/p/search/", "K": "https://map.kakao.com/?q="}
SEARCH_PATH = {"N": "/api/search/all?", "K": "/mapsearch/map?"}


def key(value):
    return re.sub(r"\s+", "", value or "").casefold()


def area(value):
    return key(value).replace("경기도", "").replace("특례시", "").removesuffix("시").removesuffix("군")


def make_driver():
    options = webdriver.ChromeOptions()
    options.add_argument("--headless=new")
    options.add_argument("--blink-settings=imagesEnabled=false")
    options.page_load_strategy = "eager"
    options.set_capability("goog:loggingPrefs", {"performance": "ALL"})
    drivers = sorted(Path.home().glob(".cache/selenium/chromedriver/mac-arm64/*/chromedriver"),
                     key=lambda p: tuple(map(int, p.parent.name.split("."))))
    driver = webdriver.Chrome(service=Service(str(drivers[-1])) if drivers else None, options=options)
    driver.set_page_load_timeout(15)
    return driver


def search(driver, platform, query):
    driver.get_log("performance")
    driver.get(HOST[platform] + quote(query))
    pending = {}
    until = time.monotonic() + 7
    while time.monotonic() < until:
        for line in driver.get_log("performance"):
            message = json.loads(line["message"])["message"]
            if message["method"] != "Network.responseReceived":
                continue
            params = message["params"]
            url = params["response"]["url"]
            if SEARCH_PATH[platform] in url or (platform == "N" and "/api/search/query-analyze?" in url):
                pending[params["requestId"]] = url
        for request_id, url in list(pending.items()):
            try:
                payload = json.loads(driver.execute_cdp_cmd("Network.getResponseBody", {"requestId": request_id})["body"])
            except (WebDriverException, ValueError):
                continue
            del pending[request_id]
            if platform == "N":
                if "/query-analyze?" in url:
                    if key((payload.get("query") or {}).get("original")) == key(query) and (payload.get("metadata", {}).get("busRoute") or {}).get("total") == 0:
                        return []
                    continue
                result = payload.get("result") or {}
                bus = (result.get("bus") or {}).get("busRoute")
                if bus is not None and key((result.get("metaInfo") or {}).get("searchedQuery")) == key(query):
                    return bus.get("list") or []
            elif key(payload.get("query")) == key(query) and not (payload.get("error_code") or payload.get("gw_error_code")):
                return payload.get("bus") or []
        time.sleep(0.15)
    return None


def candidates(platform, raw):
    fields = {
        "N": ("id", "name", "cityName", "startPoint", "endPoint"),
        "K": ("DOCID", "BUS_NAME", "RGN_D2_NAME", "START_POINT", "END_POINT"),
    }
    return [dict(zip(("id", "name", "city", "start", "end"), (item.get(f) for f in fields[platform]))) for item in raw]


def audit(driver, row, cache, duplicates):
    platform, number = row["platform"], row["bus_nmdb"]
    cities = {area(s) for s in row["csv_city"].split(" | ") if s}
    names = list(dict.fromkeys([name for name in row["csv_platform_name"].split(" | ") + [number] if name]))
    queries = names + [f"{city} {name} 버스" for city in row["csv_city"].split(" | ") if city for name in names]
    attempts = []
    for query in queries:
        if query not in cache:
            try:
                cache[query] = search(driver, platform, query)
            except WebDriverException:
                cache[query] = None
        raw = cache[query]
        hits = candidates(platform, raw or [])
        attempts.append({"query": query, "response": raw is not None, "candidates": hits})
        matching = [hit for hit in hits if area(hit["city"]) in cities and key(hit["name"]) in {key(n) for n in names}]
        if len(matching) == 1 and duplicates[platform, area(matching[0]["city"]), key(matching[0]["name"])] == 1:
            return {"status": "matched_number_city", "match": matching[0], "attempts": attempts}
    return {"status": "needs_review" if any(a["candidates"] for a in attempts)
            else "no_search_result" if attempts and all(a["response"] for a in attempts)
            else "lookup_incomplete", "attempts": attempts}


def worker(platform, index, rows, duplicates):
    path = OUT / f"live_{platform}_{index}.jsonl"
    done = set()
    if path.exists():
        with path.open() as existing:
            done = {json.loads(line)["rte_id"] for line in existing}
    todo = [row for row in rows if row["rte_id"] not in done]
    if not todo:
        return
    counts = Counter()
    with make_driver() as driver, path.open("a") as log:
        cache = {}
        for i, row in enumerate(todo, 1):
            result = audit(driver, row, cache, duplicates)
            counts[result["status"]] += 1
            log.write(json.dumps({**row, **result, "checked_at": datetime.now().astimezone().isoformat()}, ensure_ascii=False) + "\n")
            log.flush()
            if i == 1 or i % 20 == 0:
                print(platform, index, i, "/", len(todo), dict(counts), flush=True)


def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--workers-per-map", type=int, default=2)
    parser.add_argument("--all", action="store_true")
    args = parser.parse_args()
    with (OUT / "csv_comparison.csv").open(encoding="utf-8-sig") as file:
        rows = list(csv.DictReader(file))
    duplicates = Counter((r["platform"], area(city), key(n))
                         for r in rows for city in r["csv_city"].split(" | ") if city
                         for n in {r["bus_nmdb"], *r["csv_platform_name"].split(" | ")} if n)
    workers = args.workers_per_map
    with ThreadPoolExecutor(2 * workers) as pool:
        tasks = []
        for platform in HOST:
            selected = [r for r in rows if r["platform"] == platform and (args.all or not r["gbn_nm"])]
            selected.sort(key=lambda r: (bool(r["gbn_nm"]), r["bus_nmdb"] != "955-1", not int(r["csv_stop_rows"])))
            tasks.extend(pool.submit(worker, platform, i, selected[i::workers], duplicates) for i in range(workers))
        for task in tasks:
            task.result()


if __name__ == "__main__":
    main()
