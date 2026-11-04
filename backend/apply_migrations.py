"""Offline inspection, or explicitly approved isolated PostgreSQL execution.

The old no-argument command no longer applies SQL via DATABASE_URL/.env. This is
intentional: unrecorded migrations and per-file commits cannot be safely inferred.
See MIGRATION_PREFLIGHT.md before preparing a review for an existing test copy.
"""
import argparse
import json
import os
import sys
from pathlib import Path

from migration_plan import MigrationPlanError, inspect_plan, migration_files as checked_migration_files

MIGRATIONS = Path(__file__).with_name("migrations")


def migration_files() -> list[Path]:
    return checked_migration_files(MIGRATIONS)


def main():
    ap = argparse.ArgumentParser(description="DB-free migration inspection / approved local test execution")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--history-file", type=Path, help="P30 identity manifest, --dry-run only")
    ap.add_argument("--apply-local-test", action="store_true")
    ap.add_argument("--review-file", type=Path, help="Independent reviewed schema/history/full-plan manifest")
    ap.add_argument("--schema", help="Existing p31_migration_test_<32 lowercase hex> schema")
    ap.add_argument("--confirm-local-test", action="store_true", help="Operator confirms an approved isolated test copy, never production")
    args = ap.parse_args()
    if args.history_file is not None and not args.dry_run:
        ap.error("--history-file requires --dry-run; no SQL was executed")
    if args.dry_run and (args.apply_local_test or args.review_file or args.schema or args.confirm_local_test):
        ap.error("dry-run cannot be combined with execution options")
    try:
        plan = inspect_plan(MIGRATIONS, args.history_file)
        if args.dry_run:
            print(json.dumps(plan, ensure_ascii=False, indent=2))
            return
        if not (args.apply_local_test and args.review_file and args.schema and args.confirm_local_test):
            raise MigrationPlanError("explicit_reviewed_local_test_execution_required")
        from migration_runner import load_review, local_test_connection_args, prepare_migrations, run_migrations
        migrations = prepare_migrations(MIGRATIONS)
        review = load_review(args.review_file, migrations, args.schema)
        connection_args = local_test_connection_args(os.environ.get("TEST_POSTGRES_URL"))
    except MigrationPlanError as error:
        sys.exit(f"마이그레이션 사전 검사 차단: {error}; DB에 접속하지 않았습니다.")
    # No database.py/.env import, no DATABASE_URL fallback, and no automatic schema
    # creation. The dedicated connection is never shared with application requests.
    try:
        import psycopg2
        connection = psycopg2.connect(**connection_args)
    except Exception:
        sys.exit("local_test_database_connection_failed; 연결 정보 원문은 출력하지 않습니다.")
    try:
        result = run_migrations(connection, migrations, review)
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except MigrationPlanError as error:
        sys.exit(str(error))
    finally:
        try:
            connection.close()
        except Exception:
            pass


if __name__ == "__main__":
    main()
