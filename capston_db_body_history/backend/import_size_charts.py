"""
브랜드 사이즈표 CSV → DB(size_charts) 넣기.

쇼핑몰 상품 페이지의 '실측 사이즈' 표를 엑셀/구글시트에 옮겨 적고 CSV 로 저장한 뒤:

    python import_size_charts.py size_charts.csv            # 추가
    python import_size_charts.py size_charts.csv --replace  # 기존 사이즈표 지우고 새로 넣기

CSV 첫 줄(제목)은 한글/영문 둘 다 된다. 양식은 size_charts_template.csv 참고.
  브랜드, 카테고리, 사이즈, 어깨, 가슴, 허리, 엉덩이, 총장, 기준, 출처 링크
  - 숫자 칸은 cm, 비어 있어도 된다
  - 기준: 단면(한쪽 폭) 또는 둘레 — 쇼핑몰 표에 적힌 대로
"""
import argparse
import csv
import sys

from sqlalchemy import delete

from database import SessionLocal
from db_models import SizeChart
from seed import init_db

HEADER_ALIASES = {
    "brand": ["브랜드", "brand"],
    "category": ["카테고리", "category"],
    "size_label": ["사이즈", "size", "size_label"],
    "shoulder_cm": ["어깨", "어깨너비", "shoulder", "shoulder_cm"],
    "chest_cm": ["가슴", "가슴단면", "chest", "chest_cm"],
    "waist_cm": ["허리", "허리단면", "waist", "waist_cm"],
    "hip_cm": ["엉덩이", "엉덩이단면", "hip", "hip_cm"],
    "length_cm": ["총장", "기장", "length", "length_cm"],
    "measure_type": ["기준", "측정기준", "measure_type"],
    "source_url": ["출처 링크", "출처", "링크", "source_url"],
}


def _column_map(header: list[str]) -> dict[str, int]:
    normalized = [h.strip().lower() for h in header]
    result = {}
    for field, aliases in HEADER_ALIASES.items():
        for alias in aliases:
            if alias.lower() in normalized:
                result[field] = normalized.index(alias.lower())
                break
    missing = [f for f in ("brand", "category", "size_label") if f not in result]
    if missing:
        sys.exit(f"CSV 제목 줄에 필수 칸이 없습니다: {missing} (브랜드, 카테고리, 사이즈)")
    return result


def _num(text: str):
    text = (text or "").strip().replace("cm", "")
    if not text:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def main() -> None:
    parser = argparse.ArgumentParser(description="사이즈표 CSV 를 DB 에 넣는다")
    parser.add_argument("csv_path")
    parser.add_argument("--replace", action="store_true", help="기존 사이즈표를 모두 지우고 넣기")
    args = parser.parse_args()

    init_db()  # 테이블이 없으면 만든다

    with open(args.csv_path, encoding="utf-8-sig", newline="") as f:
        rows = list(csv.reader(f))
    if not rows:
        sys.exit("CSV 가 비어 있습니다")
    cols = _column_map(rows[0])

    def cell(row, field):
        i = cols.get(field)
        return row[i].strip() if i is not None and i < len(row) else ""

    charts = []
    for row in rows[1:]:
        if not any(c.strip() for c in row):
            continue
        charts.append(SizeChart(
            brand=cell(row, "brand"),
            category=cell(row, "category"),
            size_label=cell(row, "size_label"),
            shoulder_cm=_num(cell(row, "shoulder_cm")),
            chest_cm=_num(cell(row, "chest_cm")),
            waist_cm=_num(cell(row, "waist_cm")),
            hip_cm=_num(cell(row, "hip_cm")),
            length_cm=_num(cell(row, "length_cm")),
            measure_type=cell(row, "measure_type") or "단면",
            source_url=cell(row, "source_url"),
        ))

    with SessionLocal() as db:
        if args.replace:
            db.execute(delete(SizeChart))
        db.add_all(charts)
        db.commit()
    print(f"사이즈표 {len(charts)}줄을 넣었습니다{' (기존 표 교체)' if args.replace else ''}")


if __name__ == "__main__":
    main()
