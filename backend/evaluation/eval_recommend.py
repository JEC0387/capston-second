"""
코디 추천 성과 표 만들기 — 서버의 /api/recommendations/stats 결과를 발표용 표로 출력한다.

    python evaluation/eval_recommend.py                       # 모든 사용자 합계
    FASHION_AUTH_TOKEN=<개인 접속 토큰> python evaluation/eval_recommend.py --mine
    python evaluation/eval_recommend.py --server http://192.168.0.10:8000 --csv rec_stats.csv

서버(uvicorn)가 켜져 있고 DB(Supabase)에 연결돼 있어야 한다. 표준 라이브러리만 쓴다.
"""
import argparse
import csv
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

LABELS = {"home": "홈", "today": "오늘", "forecast": "5일 예보", "situation": "상황별"}


def fetch(server: str, mine: bool) -> dict:
    path = "/api/recommendations/stats" if mine else "/api/recommendations/stats/all"
    headers = {}
    variable = "FASHION_AUTH_TOKEN" if mine else "FASHION_ANALYTICS_ADMIN_TOKEN"
    token = os.environ.get(variable, "")
    host = urllib.parse.urlparse(server).hostname
    if not token or not (server.startswith("https://") or host in ("localhost", "127.0.0.1")):
        sys.exit(f"통계에는 {variable}과 HTTPS 서버 주소가 필요합니다")
    headers["Authorization"] = "Bearer " + token
    # Never forward a credential through an HTTP redirect.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None
    opener = urllib.request.build_opener(NoRedirect)
    req = urllib.request.Request(server.rstrip("/") + path, headers=headers)
    try:
        with opener.open(req, timeout=10) as res:
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
    ap.add_argument("--mine", action="store_true", help="FASHION_AUTH_TOKEN으로 인증한 내 통계")
    ap.add_argument("--csv", help="표를 CSV 로도 저장")
    args = ap.parse_args()

    stats = fetch(args.server, args.mine)
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
