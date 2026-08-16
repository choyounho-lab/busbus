#!/usr/bin/env python3
import argparse
import csv
import time
from pathlib import Path

from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter


def wait_for_rows(path: Path, expected: int) -> None:
    while True:
        if path.exists():
            with path.open("r", encoding="utf-8-sig", newline="") as handle:
                count = sum(1 for _ in handle)
            if count >= expected:
                return
        time.sleep(30)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--wait-for-rows", type=int, default=0)
    args = parser.parse_args()
    if args.wait_for_rows:
        wait_for_rows(args.input, args.wait_for_rows)

    with args.input.open("r", encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.reader(handle))
    header, data = rows[0], rows[1:]
    data.sort(key=lambda row: (row[3], row[1], row[0]))

    wb = Workbook()
    ws = wb.active
    ws.title = "경기도 전체 버스"
    ws.append(header)
    for row in data:
        ws.append(row)

    fills = {
        "base": PatternFill("solid", fgColor="34495E"),
        "네이버": PatternFill("solid", fgColor="03C75A"),
        "카카오": PatternFill("solid", fgColor="F7E600"),
        "TAGO": PatternFill("solid", fgColor="2F75B5"),
    }
    for cell in ws[1]:
        source = next((name for name in ("네이버", "카카오", "TAGO") if cell.value.startswith(name + "_")), "base")
        cell.fill = fills[source]
        cell.font = Font(bold=True, color="000000" if source == "카카오" else "FFFFFF")
        cell.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)

    ws.freeze_panes = "E2"
    ws.auto_filter.ref = ws.dimensions
    ws.row_dimensions[1].height = 42
    for index, name in enumerate(header, 1):
        width = 14
        if name in ("노선ID", "출발지", "도착지") or name.endswith(("_출발지", "_도착지")):
            width = 20
        if name in ("버스번호", "지역코드", "지역"):
            width = 13
        ws.column_dimensions[get_column_letter(index)].width = width
    for row in ws.iter_rows(min_row=2):
        for cell in row:
            cell.alignment = Alignment(vertical="center")

    summary = wb.create_sheet("요약")
    summary.append(["항목", "값"])
    summary.append(["전체 노선 수", len(data)])
    summary.append(["지역 수", len({row[3] for row in data})])
    for source, offset in (("네이버", 4), ("카카오", 24), ("TAGO", 44)):
        summary.append([source + " 정보 확인 노선", sum(1 for row in data if len(row) > offset and row[offset] == source)])
    for cell in summary[1]:
        cell.fill = fills["base"]
        cell.font = Font(bold=True, color="FFFFFF")
    summary.column_dimensions["A"].width = 25
    summary.column_dimensions["B"].width = 15
    summary.freeze_panes = "A2"

    args.output.parent.mkdir(parents=True, exist_ok=True)
    wb.save(args.output)
    print(args.output)


if __name__ == "__main__":
    main()
