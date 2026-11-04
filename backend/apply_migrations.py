"""
migrations/ 의 SQL 파일(001, 002, …)을 번호 순서대로 Supabase DB 에 적용한다.

    cd backend
    python apply_migrations.py            # 승인된 DB에만 적용; 실제 적용 이력·스키마 사전 검토 필수
    python apply_migrations.py --dry-run  # DB 접속 없이 번호 충돌·파일 체크섬 확인
    python apply_migrations.py --dry-run --history-file reviewed-history.json

기존 SQL을 순서대로 실행한다. 파일 번호 충돌 검사는 실제 DB 스키마 안전성 검증을 대체하지 않는다.
.env 의 DATABASE_URL 을 쓴다 (비밀번호를 화면에 출력하지 않음).
"""
import argparse
import json
import sys
from pathlib import Path

from migration_plan import MigrationPlanError, inspect_plan, migration_files as checked_migration_files

MIGRATIONS = Path(__file__).with_name("migrations")


def migration_files() -> list[Path]:
    return checked_migration_files(MIGRATIONS)


def main():
    ap = argparse.ArgumentParser(description="DB 마이그레이션 적용")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--history-file", type=Path,
                    help="독립 확인한 적용 이력 JSON; --dry-run 전용, 실제 DB 스키마 검증은 아님")
    args = ap.parse_args()
    if args.history_file is not None and not args.dry_run:
        ap.error("--history-file requires --dry-run; no SQL was executed")
    try:
        files = migration_files()
        plan = inspect_plan(MIGRATIONS, args.history_file)
    except MigrationPlanError as error:
        sys.exit(f"마이그레이션 사전 검사 차단: {error}; DB에 접속하지 않았습니다.")
    if args.dry_run:
        print(json.dumps(plan, ensure_ascii=False, indent=2))
        return
    # Importing database may load .env / initialise an engine. Do it only after the
    # offline preflight has passed, never for --dry-run or duplicate SQL numbers.
    from database import database_status, engine
    print("적용할 파일:", ", ".join(f.name for f in files))
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
