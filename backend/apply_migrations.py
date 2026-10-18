"""
migrations/ 의 SQL 파일(001, 002, …)을 번호 순서대로 Supabase DB 에 적용한다.

    cd backend
    python apply_migrations.py            # 모두 적용 (여러 번 실행해도 안전 — 모든 파일이 IF NOT EXISTS)
    python apply_migrations.py --dry-run  # 적용할 파일 목록만 보기

Supabase SQL Editor 에 하나씩 붙여 넣는 것과 같다. 표 · 칸을 더하기만 하고 데이터는 지우지 않는다.
.env 의 DATABASE_URL 을 쓴다 (비밀번호를 화면에 출력하지 않음).
"""
import argparse
import sys
from pathlib import Path

from database import database_status, engine

MIGRATIONS = Path(__file__).with_name("migrations")


def migration_files() -> list[Path]:
    return sorted(MIGRATIONS.glob("[0-9][0-9][0-9]_*.sql"))


def main():
    ap = argparse.ArgumentParser(description="DB 마이그레이션 적용")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    files = migration_files()
    print("적용할 파일:", ", ".join(f.name for f in files))
    if args.dry_run:
        return
    if engine is None:
        sys.exit("DATABASE_URL 이 없어요. backend/.env 에 Supabase 주소를 넣어 주세요.")
    raw = engine.raw_connection()
    try:
        raw.autocommit = True          # 파일 안의 BEGIN; … COMMIT; 을 그대로 실행
        cur = raw.cursor()
        for f in files:
            cur.execute(f.read_text(encoding="utf-8"))
            print(f"  ✓ {f.name}")
        cur.close()
    except Exception as e:  # noqa: BLE001 — 어떤 파일에서 멈췄는지 알려 주고 끝낸다
        sys.exit(f"적용 중 오류: {type(e).__name__} — 위에 ✓ 가 없는 첫 파일을 SQL Editor 에서 확인해 주세요")
    finally:
        raw.close()
    status = database_status()
    print("DB 상태:", "준비됨" if status.get("ready") else status.get("status"),
          "· 빠진 항목: " + ", ".join(status["missing"]) if status.get("missing") else "· 빠진 항목 없음")


if __name__ == "__main__":
    main()
