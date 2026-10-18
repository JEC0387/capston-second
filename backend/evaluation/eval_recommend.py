"""
코디 추천 성과 표 만들기 — 서버의 /api/recommendations/stats 결과를 발표용 표로 출력한다.

    python evaluation/eval_recommend.py                       # 모든 사용자 합계
    python evaluation/eval_recommend.py --device <백업 코드>   # 한 사람 것만
    python evaluation/eval_recommend.py --server http://192.168.0.10:8000 --csv rec_stats.csv

서버(uvicorn)가 켜져 있고 DB(Supabase)에 연결돼 있어야 한다. 표준 라이브러리만 쓴다.
"""
import argparse
import csv
import json
import sys
import urllib.error
import urllib.request

LABELS = {"home": "홈", "today": "오늘", "forecast": "5일 예보", "situation": "상황별"}


def fetch(server: str, device: "str | None") -> dict:
    path = "/api/recommendations/stats" if device else "/api/recommendations/stats/all"
    req = urllib.request.Request(server.rstrip("/") + path, headers={"X-Device-Id": device} if device else {})
    try:
        with urllib.request.urlopen(req, timeout=10) as res:
            return json.loads(res.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        sys.exit(f"서버 오류 {e.code}: {e.read().decode('utf-8', 'replace')[:200]} (DB 연결 · 003 마이그레이션 확인)")
    except urllib.error.URLError as e:
        sys.exit(f"서버에 연결하지 못했어요: {e.reason} — uvicorn 이 켜져 있는지, --server 주소가 맞는지 확인")


def pct(v) -> str:
    return "-" if v is None else f"{v * 100:.1f}%"


def rows(stats: dict) -> list[list[str]]:
    out = []
    groups = [("전체", stats["total"])] + [(LABELS.get(k, k), g) for k, g in stats["byContext"].items()]
    for name, g in groups:
        out.append([name, str(g["shown"]), f'{g["liked"]} ({pct(g["likeRate"])})', pct(g["ratedLikeRate"]),
                    f'{g["worn"]} ({pct(g["wornRate"])})', f'{g["wornPartial"]} ({pct(g["wornPartialRate"])})'])
    return out


def main():
    ap = argparse.ArgumentParser(description="코디 추천 성과 표")
    ap.add_argument("--server", default="http://localhost:8000")
    ap.add_argument("--device", help="한 사람 것만 볼 때 그 폰의 백업 코드(기기 번호)")
    ap.add_argument("--csv", help="표를 CSV 로도 저장")
    args = ap.parse_args()

    stats = fetch(args.server, args.device)
    header = ["화면", "추천 코디", "👍", "평가 중 👍", "그대로 입음", "일부 입음"]
    table = rows(stats)
    print(f"기간: {stats['since'] or '-'} ~ {stats['until'] or '-'} · 사용자 {stats['users']}명\n")
    print("| " + " | ".join(header) + " |")
    print("|" + "---|" * len(header))
    for r in table:
        print("| " + " | ".join(r) + " |")
    if stats["total"]["shown"] < 30:
        print("\n※ 추천 코디가 30개 미만이라 비율이 크게 흔들릴 수 있어요. 개수를 같이 적어 주세요.")
    if args.csv:
        with open(args.csv, "w", newline="", encoding="utf-8-sig") as f:
            w = csv.writer(f)
            w.writerow(header)
            w.writerows(table)
        print(f"\nCSV 저장: {args.csv}")


if __name__ == "__main__":
    main()
