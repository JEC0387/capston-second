"""
옷 종류 분류 정확도 계산 — 서버의 DeepFashion2 분류(/api/clothing/classify 와 같은 함수)를 사진 폴더에 돌려 본다.

사용법 (backend 폴더에서):
    python evaluation/eval_classify.py evaluation/clothes
    python evaluation/eval_classify.py evaluation/clothes --conf 0.3

사진 폴더 구조 — 폴더 이름이 정답이다 (영어 폴더 이름 그대로 쓰기):
    evaluation/clothes/
        short_sleeved_shirt/   반팔 상의 (반팔티, 반팔 셔츠, 블라우스 …)
        long_sleeved_shirt/    긴팔 상의 (긴팔티, 셔츠, 니트, 맨투맨, 후드티 …)
        sleeveless/            민소매 상의
        short_sleeved_outwear/ 반팔 아우터
        long_sleeved_outwear/  긴팔 아우터 (자켓, 코트, 가디건, 점퍼 …)
        shorts/                반바지
        trousers/              긴바지 (청바지, 슬랙스, 면바지 …)
        skirt/                 치마
        short_sleeved_dress/   반팔 원피스
        long_sleeved_dress/    긴팔 원피스
        sleeveless_dress/      민소매 원피스
        bag/                   가방 (핸드백, 백팩, 캐리어)
    폴더마다 3~5장, 전체 30~50장이면 충분하다. 쓰지 않는 폴더는 만들지 않아도 된다.

정답 판정 — 앱처럼 "가장 크게 잡힌 첫 번째 후보"를 AI 답으로 본다.
  - 소분류 정확도: 반팔 상의 / 긴바지 … 까지 맞았는지
  - 대분류 정확도: 상의 / 하의 / 아우터 / 원피스 / 가방 만 맞았는지
  - 후보 안에 정답 포함: 첫 번째는 틀렸지만 후보 목록 안에는 있었는지 (앱에서 사용자가 고를 수 있음)

출력
  - 화면에 표
  - evaluation/results/classify_per_image.csv  (사진별 결과)
  - evaluation/results/classify_summary.md     (발표용 표 + 대분류 혼동표)
"""
import argparse
import csv
import sys
import time
from pathlib import Path

# backend 폴더의 fashion_detection.py 를 불러올 수 있게 경로 추가
BACKEND_DIR = Path(__file__).resolve().parent.parent
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

# 정답 폴더 이름 → (소분류, 대분류) — 앱 ClothingCategories.fromModelLabel 과 같은 묶음
GROUPS = {
    "short_sleeved_shirt": ("반팔 상의", "상의"),
    "long_sleeved_shirt": ("긴팔 상의", "상의"),
    "sleeveless": ("민소매", "상의"),
    "short_sleeved_outwear": ("반팔 아우터", "아우터"),
    "long_sleeved_outwear": ("긴팔 아우터", "아우터"),
    "shorts": ("반바지", "하의"),
    "trousers": ("긴바지", "하의"),
    "skirt": ("치마", "하의"),
    "short_sleeved_dress": ("반팔 원피스", "원피스"),
    "long_sleeved_dress": ("긴팔 원피스", "원피스"),
    "sleeveless_dress": ("민소매 원피스", "원피스"),
    "bag": ("가방", "가방"),
}
# 모델이 내는 이름 → 정답 폴더 이름
MODEL_TO_GROUP = {
    "vest": "sleeveless", "sling": "sleeveless",
    "vest_dress": "sleeveless_dress", "sling_dress": "sleeveless_dress",
    "handbag": "bag", "backpack": "bag", "suitcase": "bag",
}
CATEGORIES = ["상의", "하의", "아우터", "원피스", "가방", "인식 실패"]
IMAGE_EXT = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def to_group(model_label: str) -> str:
    return MODEL_TO_GROUP.get(model_label, model_label)


def collect_images(folder: Path) -> list[tuple[Path, str]]:
    items = []
    for sub in sorted(p for p in folder.iterdir() if p.is_dir()):
        if sub.name not in GROUPS:
            print(f"[건너뜀] 알 수 없는 폴더 이름: {sub.name}  (쓸 수 있는 이름: {', '.join(GROUPS)})")
            continue
        for img in sorted(sub.iterdir()):
            if img.suffix.lower() in IMAGE_EXT:
                items.append((img, sub.name))
    return items


def evaluate(items: list[tuple[Path, str]], classify) -> tuple[list[dict], dict]:
    """classify(PIL.Image) → 후보 라벨 목록(크게 잡힌 순). 테스트할 때 가짜 함수를 넣을 수 있게 분리."""
    from PIL import Image

    rows = []
    for path, truth in items:
        start = time.perf_counter()
        try:
            with Image.open(path) as im:
                im.load()
                labels = classify(im.convert("RGB"))
        except Exception as e:  # 사진 하나가 깨져 있어도 나머지는 계속 (깨진 사진은 계산에서 뺌)
            print(f"[건너뜀] {truth}/{path.name}: 사진을 읽거나 분류하지 못했어요 ({e})")
            continue
        elapsed = time.perf_counter() - start

        groups = [to_group(label) for label in labels]
        pred = groups[0] if groups else None
        truth_sub, truth_cat = GROUPS[truth]
        pred_sub, pred_cat = GROUPS.get(pred, ("인식 실패", "인식 실패")) if pred else ("인식 실패", "인식 실패")
        rows.append({
            "사진": f"{truth}/{path.name}",
            "정답(소분류)": truth_sub,
            "AI(소분류)": pred_sub,
            "정답(대분류)": truth_cat,
            "AI(대분류)": pred_cat,
            "소분류 맞음": pred == truth,
            "대분류 맞음": pred_cat == truth_cat,
            "후보에 정답 포함": truth in groups,
            "후보": " / ".join(labels),
            "시간(초)": round(elapsed, 2),
        })

    n = len(rows)
    if n == 0:
        sys.exit("분류에 성공한 사진이 없습니다")
    summary = {
        "사진 수": n,
        "소분류 정확도(%)": sum(r["소분류 맞음"] for r in rows) / n * 100,
        "대분류 정확도(%)": sum(r["대분류 맞음"] for r in rows) / n * 100,
        "후보에 정답 포함(%)": sum(r["후보에 정답 포함"] for r in rows) / n * 100,
        "인식 실패(%)": sum(r["AI(대분류)"] == "인식 실패" for r in rows) / n * 100,
        "평균 처리 시간(초)": sum(r["시간(초)"] for r in rows) / n,
    }
    return rows, summary


def to_markdown(rows: list[dict], summary: dict, conf: float) -> str:
    lines = [
        "# 옷 종류 분류 정확도 (DeepFashion2 YOLOv8-seg + COCO 가방류)",
        "",
        f"- 사진 {summary['사진 수']}장, 신뢰도 기준값 {conf}",
        "- AI 답 = 가장 크게 잡힌 첫 번째 후보 (앱 기본 선택과 같음)",
        "",
        "| 지표 | 값 |",
        "|---|---|",
    ]
    for k, v in summary.items():
        lines.append(f"| {k} | {v:.2f} |" if isinstance(v, float) else f"| {k} | {v} |")

    # 소분류별 정확도
    lines += ["", "## 종류별 결과", "", "| 정답 종류 | 사진 수 | 맞은 수 | 정확도(%) |", "|---|---|---|---|"]
    by_truth: dict[str, list[dict]] = {}
    for r in rows:
        by_truth.setdefault(r["정답(소분류)"], []).append(r)
    for name, rs in by_truth.items():
        ok = sum(r["소분류 맞음"] for r in rs)
        lines.append(f"| {name} | {len(rs)} | {ok} | {ok / len(rs) * 100:.0f} |")

    # 대분류 혼동표
    used = [c for c in CATEGORIES if any(r["정답(대분류)"] == c or r["AI(대분류)"] == c for r in rows)]
    lines += ["", "## 대분류 혼동표 (행 = 정답, 열 = AI 답)", "",
              "| 정답 \\ AI | " + " | ".join(used) + " |", "|---|" + "---|" * len(used)]
    for t in used:
        if t == "인식 실패":
            continue
        counts = [sum(1 for r in rows if r["정답(대분류)"] == t and r["AI(대분류)"] == p) for p in used]
        lines.append(f"| {t} | " + " | ".join(str(c) for c in counts) + " |")

    wrong = [r for r in rows if not r["소분류 맞음"]]
    if wrong:
        lines += ["", "## 틀린 사진", "", "| 사진 | 정답 | AI 답 | 후보 |", "|---|---|---|---|"]
        for r in wrong:
            lines.append(f"| {r['사진']} | {r['정답(소분류)']} | {r['AI(소분류)']} | {r['후보'] or '-'} |")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description="옷 종류 분류 정확도 측정")
    parser.add_argument("folder", help="정답 폴더들이 들어 있는 사진 폴더 (예: evaluation/clothes)")
    parser.add_argument("--conf", type=float, default=0.3, help="신뢰도 기준값 (앱 기본 0.3)")
    args = parser.parse_args()

    folder = Path(args.folder)
    if not folder.is_dir():
        sys.exit(f"폴더가 없습니다: {folder}")
    items = collect_images(folder)
    if not items:
        sys.exit("평가할 사진이 없습니다. 폴더 구조를 확인하세요 (이 파일 맨 위 설명 참고)")

    import fashion_detection  # 무거운 모델은 사진이 있을 때만 불러온다

    def classify(image):
        result = fashion_detection.classify_clothing(image, conf_threshold=args.conf)
        return [c["label"] for c in result["candidates"]]

    print(f"사진 {len(items)}장 평가 중… (처음에는 모델을 불러오느라 조금 걸려요)")
    rows, summary = evaluate(items, classify)

    out_dir = Path(__file__).parent / "results"
    out_dir.mkdir(exist_ok=True)
    with (out_dir / "classify_per_image.csv").open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    md = to_markdown(rows, summary, args.conf)
    (out_dir / "classify_summary.md").write_text(md, encoding="utf-8")
    print(md)
    print(f"저장: {out_dir / 'classify_summary.md'}, {out_dir / 'classify_per_image.csv'}")


if __name__ == "__main__":
    main()
