"""Explicitly approved, local-test-only PostgreSQL migration runner.

No connection is opened here. SQL/history files are immutable inputs; this is not
a general SQL parser or an automatic baseline/adoption tool for existing databases.
"""
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit

from migration_plan import MigrationPlanError, migration_files


SCHEMA = re.compile(r"p31_migration_test_[a-f0-9]{32}\Z")
LEDGER = "_fashionai_migration_history"
IDENT = r"[a-z_][a-z0-9_]*"
IDENT_LIST = rf"{IDENT}(?:\s*,\s*{IDENT})*"
TYPE = r"(?:VARCHAR\s*\(\s*[1-9][0-9]{0,4}\s*\)|CHAR\s*\(\s*[1-9][0-9]{0,4}\s*\)|INTEGER|BIGINT|SERIAL|BOOLEAN|JSON|BYTEA|TIMESTAMPTZ|TEXT|REAL|DOUBLE PRECISION)"
MODIFIER = rf"(?:NOT NULL|NULL|PRIMARY KEY|UNIQUE|REFERENCES {IDENT}\s*\(\s*{IDENT}\s*\)(?: ON DELETE CASCADE)?|DEFAULT (?:now\s*\(\s*\)|[+-]?[0-9]+|TRUE|FALSE|\?))"
COLUMN = re.compile(rf"{IDENT}\s+{TYPE}(?:\s+{MODIFIER})*\Z", re.I)


def _column_or_constraint(definition):
    constraint = rf"CONSTRAINT {IDENT} (?:UNIQUE|PRIMARY KEY)\s*\(\s*{IDENT_LIST}\s*\)"
    return bool(COLUMN.fullmatch(definition.strip()) or re.fullmatch(constraint, definition.strip(), re.I))


def _ddl_is_supported(structural):
    # Strict current-project grammar, not a general SQL sandbox. Arbitrary DEFAULT
    # functions, CHECK/GENERATED expressions, custom types and qualified names are
    # deliberately unsupported: they can invoke code outside the reviewed schema.
    table = re.fullmatch(rf"CREATE TABLE(?: IF NOT EXISTS)? ({IDENT})\s*\((.*)\)", structural, re.I)
    if table:
        if table[1].lower().startswith("_fashionai_"):
            return False
        fields = []
        depth = start = 0
        for index, char in enumerate(table[2]):
            if char == '(':
                depth += 1
            elif char == ')':
                depth -= 1
                if depth < 0:
                    return False
            elif char == ',' and depth == 0:
                fields.append(table[2][start:index]); start = index + 1
        fields.append(table[2][start:])
        return depth == 0 and all(_column_or_constraint(field) for field in fields)
    index = re.fullmatch(rf"CREATE(?: UNIQUE)? INDEX(?: IF NOT EXISTS)? {IDENT} ON ({IDENT})\s*\(\s*{IDENT_LIST}\s*\)", structural, re.I)
    if index:
        return not index[1].lower().startswith("_fashionai_")
    alter = re.fullmatch(rf"ALTER TABLE ({IDENT}) ADD COLUMN(?: IF NOT EXISTS)? (.*)", structural, re.I)
    return bool(alter and not alter[1].lower().startswith("_fashionai_") and COLUMN.fullmatch(alter[2]))


@dataclass(frozen=True)
class Migration:
    name: str
    sha256: str
    statements: tuple[str, ...]

    @property
    def version(self):
        return self.name[:3]


@dataclass(frozen=True)
class Review:
    schema: str
    applied: tuple[str, ...]


def transactional_statements(sql: str) -> tuple[str, ...]:
    """Accept the limited DDL used by reviewed repository migrations only.

    Lex semicolons outside strings/comments; require exactly one outer BEGIN /
    COMMIT pair and never send it to PostgreSQL. Reject procedural SQL, session
    commands, qualified DDL targets and non-transactional/concurrent operations.
    Unsupported SQL requires a separate implementation/review, not a bypass flag.
    """
    if "\\" in sql or "\x00" in sql:
        raise MigrationPlanError("unsupported_sql_escape")
    statements = []
    start = 0
    code = []
    i = 0
    state = "code"
    depth = 0
    while i < len(sql):
        c = sql[i]
        nxt = sql[i:i+2]
        if state == "line":
            if c in "\r\n":
                state = "code"
                code.append(" ")
        elif state == "block":
            if nxt == "/*":
                depth += 1; i += 1
            elif nxt == "*/":
                depth -= 1; i += 1
                if depth == 0:
                    state = "code"; code.append(" ")
        elif state == "string":
            if c == "'":
                if nxt == "''":
                    i += 1
                else:
                    state = "code"; code.append(" ")
        elif nxt == "--":
            state = "line"; i += 1; code.append(" ")
        elif nxt == "/*":
            state = "block"; depth = 1; i += 1; code.append(" ")
        elif c == "'":
            state = "string"; code.append("?")
        elif c in ('"', '$'):
            raise MigrationPlanError("unsupported_quoted_or_procedural_sql")
        elif c == ";":
            normalized = " ".join("".join(code).split())
            if normalized:
                statements.append((sql[start:i].strip(), normalized))
            start = i + 1; code = []
        else:
            code.append(c)
        i += 1
    if state not in ("code", "line") or "".join(code).strip():
        raise MigrationPlanError("unterminated_sql")
    if len(statements) < 3 or statements[0][1].upper() != "BEGIN" or statements[-1][1].upper() != "COMMIT":
        raise MigrationPlanError("single_transaction_envelope_required")
    body = statements[1:-1]
    for _, structural in body:
        if not _ddl_is_supported(structural):
            raise MigrationPlanError("unsupported_migration_statement")
    return tuple(text for text, _ in body)


def prepare_migrations(directory: Path) -> tuple[Migration, ...]:
    result = []
    for path in migration_files(directory):
        if path.is_symlink() or path.stat().st_size > 1024 * 1024:
            raise MigrationPlanError("unsafe_migration_file")
        try:
            payload = path.read_bytes()
            statements = transactional_statements(payload.decode("utf-8"))
        except (OSError, UnicodeError):
            raise MigrationPlanError("migration_file_unreadable") from None
        result.append(Migration(path.name, hashlib.sha256(payload).hexdigest(), statements))
    if not result:
        raise MigrationPlanError("empty_migration_plan")
    return tuple(result)


def load_review(path: Path, migrations: tuple[Migration, ...], schema: str) -> Review:
    if not SCHEMA.fullmatch(schema):
        raise MigrationPlanError("isolated_test_schema_required")
    try:
        if path.stat().st_size > 1024 * 1024:
            raise MigrationPlanError("review_too_large")
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError):
        raise MigrationPlanError("review_unreadable") from None
    expected = [{"file": m.name, "sha256": m.sha256} for m in migrations]
    if not isinstance(value, dict) or type(value.get("version")) is not int or value["version"] != 1:
        raise MigrationPlanError("invalid_execution_review")
    if value.get("files") != expected:
        raise MigrationPlanError("reviewed_plan_changed")
    approval = value.get("schemaReview")
    if (not isinstance(approval, dict) or approval.get("schema") != schema or
        approval.get("approved") is not True or not isinstance(approval.get("reference"), str) or
        not 1 <= len(approval["reference"].strip()) <= 200):
        raise MigrationPlanError("independent_schema_review_required")
    applied = value.get("applied")
    if not isinstance(applied, list) or len(applied) > len(expected) or applied != expected[:len(applied)]:
        raise MigrationPlanError("reviewed_history_must_be_applied_prefix")
    return Review(schema, tuple(item["file"] for item in applied))


def local_test_connection_args(raw: str | None) -> dict:
    """Never loads .env; URL query/options cannot override validated connection fields."""
    try:
        url = urlsplit(raw or "")
        port = url.port if url.port is not None else 5432
        if (url.scheme not in ("postgresql", "postgres") or url.hostname not in ("127.0.0.1", "localhost", "::1") or
            url.path != "/fashionai_test" or url.query or url.fragment or not url.username or not 1 <= port <= 65535):
            raise ValueError()
        return {"host": url.hostname, "port": port, "dbname": "fashionai_test", "user": unquote(url.username),
                "password": unquote(url.password or ""), "connect_timeout": 5}
    except ValueError:
        raise MigrationPlanError("explicit_local_test_postgres_url_required") from None


def _check_history(rows, migrations, review):
    expected = [(m.version, m.name, m.sha256) for m in migrations]
    actual = [tuple(row) for row in rows]
    if len(actual) > len(expected) or actual != expected[:len(actual)]:
        raise MigrationPlanError("database_history_not_matching_prefix")
    if len(actual) < len(review.applied):
        raise MigrationPlanError("database_history_behind_reviewed_baseline")
    return len(actual)


def run_migrations(connection, migrations: tuple[Migration, ...], review: Review) -> dict:
    """One atomic batch: DDL and checksum ledger commit together, or roll back.

    Requires an already approved/connected local test DB. Contract tests use a fake
    DBAPI connection; only separate PostgreSQL tests can establish actual SQL behavior.
    """
    if not SCHEMA.fullmatch(review.schema) or not migrations:
        raise MigrationPlanError("isolated_test_schema_required")
    if review.applied != tuple(m.name for m in migrations[:len(review.applied)]):
        raise MigrationPlanError("reviewed_history_must_be_applied_prefix")
    if connection.autocommit:
        raise MigrationPlanError("transactional_connection_required")
    cursor = None
    committing = False
    try:
        cursor = connection.cursor()
        cursor.execute("SELECT current_database()")
        if cursor.fetchone() != ("fashionai_test",):
            raise MigrationPlanError("test_database_required")
        cursor.execute("SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_namespace WHERE nspname = %s)", (review.schema,))
        if cursor.fetchone() != (True,):
            raise MigrationPlanError("reviewed_test_schema_missing")
        cursor.execute(f'SET LOCAL search_path TO "{review.schema}"')
        cursor.execute("SET LOCAL standard_conforming_strings = on")
        cursor.execute("SET LOCAL lock_timeout = '5s'")
        cursor.execute("SET LOCAL statement_timeout = '60s'")
        lock_key = int.from_bytes(hashlib.sha256(review.schema.encode()).digest()[:8], "big", signed=True)
        cursor.execute("SELECT pg_catalog.pg_try_advisory_xact_lock(%s)", (lock_key,))
        if cursor.fetchone() != (True,):
            raise MigrationPlanError("migration_runner_busy")
        table = f'"{review.schema}"."{LEDGER}"'
        cursor.execute("SELECT pg_catalog.to_regclass(%s)", (table,))
        exists = cursor.fetchone()[0] is not None
        baseline = 0
        if not exists:
            # Explicit reviewed history only. Never inspect IF NOT EXISTS effects
            # or existing table names and infer that a historical SQL file ran.
            cursor.execute(f"CREATE TABLE {table} (version CHAR(3) PRIMARY KEY, filename TEXT NOT NULL UNIQUE, "
                           "sha256 CHAR(64) NOT NULL, origin TEXT NOT NULL, applied_at TIMESTAMPTZ NOT NULL DEFAULT now())")
            for migration in migrations[:len(review.applied)]:
                cursor.execute(f"INSERT INTO {table} (version, filename, sha256, origin) VALUES (%s, %s, %s, %s)",
                               (migration.version, migration.name, migration.sha256, "reviewed_baseline"))
            baseline = len(review.applied)
        cursor.execute(f"SELECT version, filename, sha256 FROM {table} ORDER BY version")
        applied = _check_history(cursor.fetchall(), migrations, review)
        for migration in migrations[applied:]:
            for statement in migration.statements:
                cursor.execute(statement)
            cursor.execute(f"INSERT INTO {table} (version, filename, sha256, origin) VALUES (%s, %s, %s, %s)",
                           (migration.version, migration.name, migration.sha256, "executed"))
        committing = True
        connection.commit()
        return {"applied": [m.name for m in migrations[applied:]], "skipped": [m.name for m in migrations[:applied]],
                "reviewedBaselineImported": baseline, "transactionCommitted": True}
    except Exception as error:
        try:
            connection.rollback()
        except Exception:
            pass
        if committing:
            raise MigrationPlanError("commit_outcome_unknown_reconnect_and_check_ledger") from None
        if isinstance(error, MigrationPlanError):
            raise
        # Driver errors may contain SQL, addresses or row data. Do not expose them.
        raise MigrationPlanError("migration_execution_failed_batch_rolled_back_or_connection_lost") from None
    finally:
        if cursor is not None:
            try:
                cursor.close()
            except Exception:
                pass
