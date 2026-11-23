"""Match the DB snapshot to the supplied November 18 CSVs by route ID."""
import csv
from collections import Counter, defaultdict
from pathlib import Path

OUT = Path(__file__).resolve().parent
DESKTOP = Path.home() / "Desktop"
PLATFORMS = {"N": "네이버", "K": "카카오", "T": "TAGO"}


def route_key(value):
    return value.strip().removeprefix("GGB")


def read_csv(path):
    with path.open(encoding="utf-8-sig", newline="") as file:
        yield from csv.DictReader(file)


def main():
    full = next(DESKTOP.glob("*/*검색명반영_20261118.csv"))
    stops = next(full.parent.glob("* 비교_20261118.csv"))
    by_route = defaultdict(list)
    for row in read_csv(full):
        by_route[route_key(row["노선ID"])].append(row)
    stop_counts = Counter(
        (code, route_key(row[f"{name}_버스ID"]))
        for row in read_csv(stops)
        for code, name in PLATFORMS.items()
        if row[f"{name}_버스ID"] and row[f"{name}_정류장명"]
    )
    counts = Counter()
    rows = []
    for db in read_csv(OUT / "db_rows.csv"):
        code = db["platform"]
        name = PLATFORMS[code]
        rid = route_key(db["rte_id"])
        sources = by_route[rid]
        positive = [row for row in sources if row[f"{name}_제공처"]]
        n_stops = stop_counts[code, rid]
        tag = "CSV에 플랫폼 기록 있음" if positive or n_stops else "CSV에 플랫폼 기록 확인 안 됨"
        if not db["gbn_nm"]:
            counts[code, tag] += 1
        rows.append({
            **db,
            "csv_route_rows": len(sources),
            "csv_provider_rows": len(positive),
            "csv_stop_rows": n_stops,
            "csv_city": " | ".join(sorted({s["지역"] for s in sources})),
            "csv_city_code": " | ".join(sorted({s["지역코드"] for s in sources})),
            "csv_platform_name": " | ".join(sorted({s[f"{name}_버스이름"] for s in sources if s[f"{name}_버스이름"]})),
            "csv_evidence": tag,
        })
    for path, data in (("csv_comparison.csv", rows), ("db_nulls.csv", [r for r in rows if not r["gbn_nm"]])):
        with (OUT / path).open("w", encoding="utf-8-sig", newline="") as file:
            writer = csv.DictWriter(file, fieldnames=rows[0].keys())
            writer.writeheader()
            writer.writerows(data)
    print(counts)
    print("No full CSV ID", sum(not row["csv_route_rows"] for row in rows))
    for row in rows:
        if row["bus_nmdb"] == "955-1":
            print(row)


if __name__ == "__main__":
    main()
