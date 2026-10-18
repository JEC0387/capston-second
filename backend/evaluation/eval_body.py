"""
체형 cm 측정 정확도 계산 — 줄자로 잰 실제 값 vs 앱 측정값.

사용법 (backend 폴더에서):
    python evaluation/eval_body.py evaluation/body_measure.csv

입력 CSV 양식: evaluation/body_measure_template.csv (엑셀로 열어 채우고 CSV UTF-8 로 저장)
  이름, 회차, 키, 실측_어깨, 앱_어깨, 실측_허리, 앱_허리, 실측_엉덩이, 앱_엉덩이, 실측_다리, 앱_다리, 비고
  - 같은 사람을 여러 번(회차 1, 2, 3 …) 찍으면 "반복 측정 편차"(같은 사람인데 앱 값이 얼마나 흔들리는지)도 계산
  - 빈 칸은 계산에서 빠진다 (예: 앱이 '측정 불가'로 나온 경우)

출력
  - 화면에 표
  - evaluation/results/body_summary.md  (발표 자료에 그대로 붙여넣을 수 있는 표)
"""
import csv
import statistics
import sys
from pathlib import Path

METRICS = [("어깨", "어깨 너비"), ("허리", "허리 너비"), ("엉덩이", "엉덩이 너비"), ("다리", "다리 길이")]


def _num(text):
    text = (text or "").strip().replace("cm", "")
    try:
        return float(text) if text else None
    except ValueError:
        return None


def load(path: Path) -> list[dict]:
    with path.open(encoding="utf-8-sig", newline="") as f:
        return [row for row in csv.DictReader(f) if any((v or "").strip() for v in row.values())]


def evaluate(rows: list[dict]) -> list[dict]:
    """항목별 오차 통계"""
    result = []
    for key, label in METRICS:
        pairs = []
        for r in rows:
            real, app = _num(r.get(f"실측_{key}")), _num(r.get(f"앱_{key}"))
            if real is not None and app is not None and real > 0:
                pairs.append((real, app, r.get("이름", "").strip()))
        if not pairs:
            continue
        errors = [app - real for real, app, _ in pairs]
        abs_err = [abs(e) for e in errors]
        pct = [abs(app - real) / real * 100 for real, app, _ in pairs]

        # 반복 측정 편차: 같은 사람의 앱 값 표준편차 평균 (2회 이상 찍은 사람만)
        by_person: dict[str, list[float]] = {}
        for _, app, name in pairs:
            by_person.setdefault(name, []).append(app)
        spreads = [statistics.pstdev(v) for v in by_person.values() if len(v) >= 2]

        result.append({
            "항목": label,
            "측정 수": len(pairs),
            "평균 오차(cm)": sum(abs_err) / len(abs_err),
            "평균 오차율(%)": sum(pct) / len(pct),
            "최대 오차(cm)": max(abs_err),
            "±2cm 이내 비율(%)": sum(1 for e in abs_err if e <= 2.0) / len(abs_err) * 100,
            "치우침(cm)": sum(errors) / len(errors),  # + 면 앱이 크게, - 면 작게 잼
            "반복 측정 편차(cm)": (sum(spreads) / len(spreads)) if spreads else None,
        })
    return result


def to_markdown(stats: list[dict], n_people: int, n_rows: int) -> str:
    cols = ["항목", "측정 수", "평균 오차(cm)", "평균 오차율(%)", "최대 오차(cm)", "±2cm 이내 비율(%)", "치우침(cm)", "반복 측정 편차(cm)"]
    lines = [
        "# 체형 cm 측정 정확도",
        "",
        f"- 참가자 {n_people}명, 측정 {n_rows}회",
        "- 실측: 벽에 등을 대고 선 상태에서 양쪽 끝에 책을 세워 대고 책 사이 거리를 줄자로 잰 **정면 폭** (둘레 아님)",
        "- 치우침: + 면 앱이 실제보다 크게, − 면 작게 잰 경향",
        "",
        "| " + " | ".join(cols) + " |",
        "|" + "---|" * len(cols),
    ]
    for s in stats:
        cells = []
        for c in cols:
            v = s[c]
            if v is None:
                cells.append("-")
            elif isinstance(v, float):
                cells.append(f"{v:+.1f}" if c == "치우침(cm)" else f"{v:.1f}")
            else:
                cells.append(str(v))
        lines.append("| " + " | ".join(cells) + " |")
    return "\n".join(lines) + "\n"


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit("사용법: python evaluation/eval_body.py evaluation/body_measure.csv")
    path = Path(sys.argv[1])
    rows = load(path)
    if not rows:
        sys.exit("CSV 에 데이터가 없습니다")
    stats = evaluate(rows)
    if not stats:
        sys.exit("실측 값과 앱 값이 둘 다 있는 줄이 없습니다")
    n_people = len({r.get("이름", "").strip() for r in rows})
    md = to_markdown(stats, n_people, len(rows))
    print(md)
    out = Path(__file__).parent / "results" / "body_summary.md"
    out.parent.mkdir(exist_ok=True)
    out.write_text(md, encoding="utf-8")
    print(f"저장: {out}")


if __name__ == "__main__":
    main()
