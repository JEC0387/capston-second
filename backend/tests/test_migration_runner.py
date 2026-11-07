"""Offline DBAPI transaction-contract tests, NOT PostgreSQL execution tests."""
from copy import deepcopy
import json
from pathlib import Path
import sys

import pytest

from migration_plan import MigrationPlanError
from migration_runner import (Migration, Review, load_review, local_test_connection_args,
                              prepare_migrations, run_migrations, transactional_statements)


SCHEMA = "p31_migration_test_" + "a" * 32


class ContractConnection:
    """A deliberately small fake: records transactions, never parses/executes SQL."""
    autocommit = False

    def __init__(self, history=None, fail_on=None, lock=True, database="fashionai_test", schema_exists=True):
        self.history = deepcopy(history)
        self.effects = []
        self.commands = []
        self.fail_on = fail_on
        self.lock = lock
        self.database = database
        self.schema_exists = schema_exists
        self.commits = self.rollbacks = 0
        self.commit_lost = False
        self._reset()

    def _reset(self):
        self.staged_history = deepcopy(self.history)
        self.staged_effects = list(self.effects)

    def cursor(self): return self
    def close(self): pass
    def fetchone(self): return self.reply
    def fetchall(self): return list(self.staged_history)

    def execute(self, sql, args=None):
        self.commands.append((sql, args))
        if sql == "SELECT current_database()": self.reply = (self.database,)
        elif "pg_namespace" in sql: self.reply = (self.schema_exists,)
        elif "pg_try_advisory_xact_lock" in sql: self.reply = (self.lock,)
        elif "to_regclass" in sql: self.reply = (None if self.staged_history is None else "ledger",)
        elif sql.startswith("CREATE TABLE") and '"_fashionai_migration_history"' in sql:
            self.staged_history = []
        elif sql.startswith("INSERT INTO") and '"_fashionai_migration_history"' in sql:
            self.staged_history.append(tuple(args[:3]))
        elif sql.startswith("SELECT version") or sql.startswith("SET LOCAL"):
            pass
        else:
            if self.fail_on and self.fail_on in sql:
                raise RuntimeError("synthetic confidential driver response")
            self.staged_effects.append(sql)

    def commit(self):
        self.commits += 1
        self.history = deepcopy(self.staged_history)
        self.effects = list(self.staged_effects)
        if self.commit_lost:
            self.commit_lost = False
            raise RuntimeError("synthetic secret commit response lost")

    def rollback(self):
        self.rollbacks += 1
        self._reset()


def plan(tmp_path):
    (tmp_path / "001_first.sql").write_text("BEGIN; CREATE TABLE first_table (id INTEGER); COMMIT;", encoding="utf-8")
    (tmp_path / "002_second.sql").write_text("BEGIN; ALTER TABLE first_table ADD COLUMN label TEXT; COMMIT;", encoding="utf-8")
    return prepare_migrations(tmp_path)


def rows(migrations): return [(m.version, m.name, m.sha256) for m in migrations]


def review_file(tmp_path, migrations, applied=0, **changes):
    records = [{"file": m.name, "sha256": m.sha256} for m in migrations]
    value = {"version": 1, "files": records, "applied": records[:applied],
             "schemaReview": {"schema": SCHEMA, "approved": True, "reference": "synthetic reviewed fixture"}}
    value.update(changes)
    path = tmp_path / "review.json"
    path.write_text(json.dumps(value), encoding="utf-8")
    return path


def test_team_lineage_seven_files_are_accepted_without_modification():
    directory = Path(__file__).parents[1] / "migrations"
    before = {p.name: p.read_bytes() for p in directory.glob("*.sql")}
    migrations = prepare_migrations(directory)
    assert [m.name for m in migrations][-3:] == [
        "005_user_logins.sql", "006_auth_credentials.sql", "007_auth_hardening.sql"]
    assert len(migrations) == 7
    assert all(m.statements for m in migrations)
    assert {p.name: p.read_bytes() for p in directory.glob("*.sql")} == before


def test_personal_history_can_append_only_user_logins_without_relabeling_applied_sql():
    """Transaction contract on real repository SQL, not a PostgreSQL execution claim."""
    directory = Path(__file__).parents[1] / "migrations" / "legacy_personal"
    migrations = prepare_migrations(directory)
    assert [m.name for m in migrations][-3:] == [
        "005_auth_credentials.sql", "006_auth_hardening.sql", "007_user_logins.sql"]
    baseline = rows(migrations[:6])
    connection = ContractConnection(history=baseline)
    review = Review(SCHEMA, tuple(m.name for m in migrations[:6]))
    result = run_migrations(connection, migrations, review)
    assert result["applied"] == ["007_user_logins.sql"]
    assert result["skipped"] == [m.name for m in migrations[:6]]
    assert connection.history[:6] == baseline
    assert connection.effects == list(migrations[6].statements)
    assert all("user_logins" in sql for sql in connection.effects)
    effects = list(connection.effects)
    assert run_migrations(connection, migrations, review)["applied"] == []
    assert connection.effects == effects


def test_personal_history_cannot_be_switched_to_team_lineage():
    directory = Path(__file__).parents[1] / "migrations"
    team = prepare_migrations(directory)
    personal = prepare_migrations(directory / "legacy_personal")
    baseline = rows(personal[:6])
    connection = ContractConnection(history=baseline)
    with pytest.raises(MigrationPlanError, match="database_history_not_matching_prefix"):
        run_migrations(connection, team, Review(SCHEMA, ()))
    assert connection.history == baseline
    assert connection.effects == [] and connection.commits == 0


def test_comments_and_string_semicolons_are_not_transaction_commands():
    actual = transactional_statements("-- BEGIN in a comment\nBEGIN; /* nested /* COMMIT; */ comment */ CREATE TABLE example (label TEXT DEFAULT 'COMMIT; it''s data'); COMMIT; -- ignored")
    assert len(actual) == 1
    assert "DEFAULT 'COMMIT; it''s data'" in actual[0]


@pytest.mark.parametrize("body", [
    "COMMIT; BEGIN; CREATE TABLE example (id INTEGER);",
    "ROLLBACK;", "SAVEPOINT x;", "SET search_path TO public;",
    "DO $$ BEGIN COMMIT; END $$;", "CALL procedure();",
    "CREATE INDEX CONCURRENTLY ix ON example (id);",
    "ALTER TABLE public.users ADD COLUMN surprise TEXT;",
    "CREATE TABLE example (id INTEGER REFERENCES public.users(id));",
    "ALTER TABLE example ADD COLUMN a INT, DROP COLUMN b;",
    "CREATE TABLE _fashionai_migration_history (id INT);",
    "CREATE TABLE example (v TEXT DEFAULT E'bad\\escape');",
    "CREATE TABLE example (v TEXT DEFAULT set_config('search_path', 'public', true));",
    "ALTER TABLE example ADD COLUMN x TEXT DEFAULT set_config('search_path', 'public', true);",
    "CREATE TABLE example (v custom_type);",
    "CREATE TABLE example (v TEXT CHECK (unsafe_function()));",
    "CREATE TABLE example (v TEXT GENERATED ALWAYS AS (unsafe_function()) STORED);",
    "CREATE INDEX ix ON example (unsafe_function(id));",
])
def test_transaction_escape_schema_escape_or_unsupported_sql_is_blocked(body):
    with pytest.raises(MigrationPlanError):
        transactional_statements("BEGIN; " + body + " COMMIT;")


def test_changed_or_unreviewed_plan_cannot_open_a_connection(tmp_path):
    migrations = plan(tmp_path)
    approved = review_file(tmp_path, migrations)
    assert load_review(approved, migrations, SCHEMA).applied == ()
    (tmp_path / "002_second.sql").write_text("BEGIN; ALTER TABLE first_table ADD COLUMN changed TEXT; COMMIT;")
    with pytest.raises(MigrationPlanError, match="reviewed_plan_changed"):
        load_review(approved, prepare_migrations(tmp_path), SCHEMA)


def test_missing_or_nonprefix_history_is_not_automatically_baselined(tmp_path):
    migrations = plan(tmp_path)
    with pytest.raises(MigrationPlanError, match="independent_schema_review_required"):
        load_review(review_file(tmp_path, migrations, schemaReview={}), migrations, SCHEMA)
    path = review_file(tmp_path, migrations)
    value = json.loads(path.read_text())
    value['applied'] = [value['files'][1]]
    path.write_text(json.dumps(value))
    with pytest.raises(MigrationPlanError, match="applied_prefix"):
        load_review(path, migrations, SCHEMA)


@pytest.mark.parametrize("url", [None, "postgresql://u:secret@remote/fashionai_test", "postgresql://u:p@localhost/production",
    "postgresql://u:p@localhost/fashionai_test?host=remote", "postgresql://u:p@localhost/fashionai_test#fragment",
    "postgresql://u:p@localhost:99999/fashionai_test", "postgresql://u:p@localhost:0/fashionai_test",
    "postgresql+psycopg2://u:p@localhost/fashionai_test"])
def test_connection_guard_rejects_nonlocal_or_overridden_target_without_echoing_url(url):
    with pytest.raises(MigrationPlanError) as error:
        local_test_connection_args(url)
    assert str(error.value) == "explicit_local_test_postgres_url_required"


def test_explicit_target_fields_are_passed_separately():
    actual = local_test_connection_args("postgresql://operator:p%40ss@127.0.0.1:55432/fashionai_test")
    assert actual == {"host":"127.0.0.1","port":55432,"dbname":"fashionai_test","user":"operator","password":"p@ss","connect_timeout":5}


def test_atomic_batch_commits_history_and_schema_then_rerun_skips(tmp_path):
    migrations = plan(tmp_path)
    review = load_review(review_file(tmp_path, migrations), migrations, SCHEMA)
    connection = ContractConnection()
    result = run_migrations(connection, migrations, review)
    assert result["applied"] == ["001_first.sql", "002_second.sql"]
    assert connection.commits == 1 and connection.rollbacks == 0
    assert connection.history == rows(migrations) and len(connection.effects) == 2
    assert all(sql.strip().upper() not in ("BEGIN", "COMMIT", "ROLLBACK") for sql, _ in connection.commands)
    again = run_migrations(connection, migrations, review)
    assert again["applied"] == [] and again["skipped"] == ["001_first.sql", "002_second.sql"]
    assert len(connection.effects) == 2


def test_failure_in_second_file_leaves_no_committed_first_file_or_ledger(tmp_path):
    migrations = plan(tmp_path)
    connection = ContractConnection(fail_on="ADD COLUMN")
    with pytest.raises(MigrationPlanError) as error:
        run_migrations(connection, migrations, Review(SCHEMA, ()))
    assert str(error.value) == "migration_execution_failed_batch_rolled_back_or_connection_lost"
    assert connection.commits == 0 and connection.rollbacks == 1
    assert connection.history is None and connection.effects == []
    connection.fail_on = None
    assert len(run_migrations(connection, migrations, Review(SCHEMA, ()))['applied']) == 2


def test_lost_commit_acknowledgement_is_unknown_not_rollback_success(tmp_path):
    migrations = plan(tmp_path)
    connection = ContractConnection()
    connection.commit_lost = True
    with pytest.raises(MigrationPlanError, match="commit_outcome_unknown"):
        run_migrations(connection, migrations, Review(SCHEMA, ()))
    assert connection.history == rows(migrations)
    assert run_migrations(connection, migrations, Review(SCHEMA, ()))['applied'] == []
    assert len(connection.effects) == 2


def test_reviewed_baseline_is_explicit_and_not_reexecuted(tmp_path):
    migrations = plan(tmp_path)
    review = load_review(review_file(tmp_path, migrations, applied=1), migrations, SCHEMA)
    connection = ContractConnection()
    result = run_migrations(connection, migrations, review)
    assert result['reviewedBaselineImported'] == 1
    assert result['skipped'] == ['001_first.sql'] and result['applied'] == ['002_second.sql']
    assert len(connection.effects) == 1 and 'ADD COLUMN' in connection.effects[0]


@pytest.mark.parametrize("kind", ["modified", "gap", "unknown", "behind"])
def test_changed_missing_unknown_or_regressed_ledger_blocks_before_ddl(tmp_path, kind):
    migrations = plan(tmp_path)
    history = rows(migrations)
    review = Review(SCHEMA, ())
    if kind == 'modified': history[0] = ('001', migrations[0].name, 'a'*64)
    elif kind == 'gap': history = history[1:]
    elif kind == 'unknown': history.append(('003','003_unknown.sql','a'*64))
    else: history = []; review = Review(SCHEMA, (migrations[0].name,))
    connection = ContractConnection(history=history)
    with pytest.raises(MigrationPlanError): run_migrations(connection, migrations, review)
    assert connection.effects == [] and connection.commits == 0
    assert connection.history == history


@pytest.mark.parametrize('configuration,reason', [({'lock':False},'migration_runner_busy'),
    ({'database':'production'},'test_database_required'), ({'schema_exists':False},'reviewed_test_schema_missing')])
def test_wrong_database_missing_schema_or_busy_runner_never_applies(tmp_path, configuration, reason):
    connection = ContractConnection(**configuration)
    with pytest.raises(MigrationPlanError, match=reason):
        run_migrations(connection, plan(tmp_path), Review(SCHEMA, ()))
    assert connection.history is None and connection.effects == [] and connection.commits == 0


def test_autocommit_and_unapproved_schema_are_blocked_before_sql(tmp_path):
    connection = ContractConnection()
    connection.autocommit = True
    with pytest.raises(MigrationPlanError, match='transactional_connection_required'):
        run_migrations(connection, plan(tmp_path), Review(SCHEMA, ()))
    assert connection.commands == []
    connection.autocommit = False
    with pytest.raises(MigrationPlanError, match='isolated_test_schema_required'):
        run_migrations(connection, plan(tmp_path), Review('public', ()))
    assert connection.commands == []


def test_no_argument_cli_never_imports_database_or_driver(monkeypatch, tmp_path):
    import builtins
    import apply_migrations
    original = builtins.__import__
    def guarded(name, *args, **kwargs):
        if name in ('database', 'psycopg2', 'dotenv'): pytest.fail('Unexpected DB import')
        return original(name, *args, **kwargs)
    plan(tmp_path)
    monkeypatch.setattr(builtins, '__import__', guarded)
    monkeypatch.setattr(apply_migrations, 'MIGRATIONS', tmp_path)
    monkeypatch.setattr(sys, 'argv', ['apply_migrations.py'])
    with pytest.raises(SystemExit, match='explicit_reviewed_local_test_execution_required'):
        apply_migrations.main()


def test_explicit_apply_never_falls_back_to_database_url(monkeypatch, tmp_path):
    import builtins
    import apply_migrations
    original = builtins.__import__
    def guarded(name, *args, **kwargs):
        if name in ('database', 'psycopg2', 'dotenv'): pytest.fail('Unexpected DB import')
        return original(name, *args, **kwargs)
    migrations = plan(tmp_path)
    path = review_file(tmp_path, migrations)
    monkeypatch.setattr(builtins, '__import__', guarded)
    monkeypatch.setattr(apply_migrations, 'MIGRATIONS', tmp_path)
    monkeypatch.delenv('TEST_POSTGRES_URL', raising=False)
    monkeypatch.setenv('DATABASE_URL', 'postgresql://private:secret@production/private')
    monkeypatch.setattr(sys, 'argv', ['apply_migrations.py', '--apply-local-test', '--confirm-local-test',
                                    '--schema', SCHEMA, '--review-file', str(path)])
    with pytest.raises(SystemExit, match='explicit_local_test_postgres_url_required') as error:
        apply_migrations.main()
    assert 'secret' not in str(error.value) and 'production' not in str(error.value)


def test_disk_changes_after_prepare_do_not_change_executed_sql(tmp_path):
    migrations = plan(tmp_path)
    review = load_review(review_file(tmp_path, migrations), migrations, SCHEMA)
    (tmp_path/'002_second.sql').write_text('BEGIN; DROP TABLE first_table; COMMIT;')
    connection = ContractConnection()
    run_migrations(connection, migrations, review)
    assert 'ADD COLUMN label' in connection.effects[-1]
    assert not any('DROP TABLE' in text for text in connection.effects)
