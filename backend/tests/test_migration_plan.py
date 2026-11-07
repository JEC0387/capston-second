"""Isolated migration identity tests. No real DB, credentials, SQL execution or network."""
import hashlib
import json
import sys

import pytest

from migration_plan import MigrationPlanError, inspect_plan, migration_files


def sql(directory, name, body="CREATE TABLE example (id INTEGER);"):
    path = directory / name
    path.write_text(body, encoding="utf-8")
    return {"file": name, "sha256": hashlib.sha256(body.encode()).hexdigest()}


def history(directory, records):
    path = directory / "reviewed-history.json"
    path.write_text(json.dumps({"version": 1, "applied": records}), encoding="utf-8")
    return path


def test_duplicate_team_and_backup_005_are_blocked_before_sql_execution(tmp_path):
    sql(tmp_path, "005_auth_credentials.sql")
    sql(tmp_path, "005_user_logins.sql", "CREATE TABLE user_logins (id INTEGER);")
    before = {p.name: p.read_bytes() for p in tmp_path.iterdir()}
    with pytest.raises(MigrationPlanError, match="duplicate_migration_version:005"):
        migration_files(tmp_path)
    assert {p.name: p.read_bytes() for p in tmp_path.iterdir()} == before


def test_missing_history_does_not_invent_applied_or_pending_state(tmp_path):
    sql(tmp_path, "005_auth_credentials.sql")
    result = inspect_plan(tmp_path)
    assert result["historyStatus"] == "not_provided"
    assert result["pendingFiles"] is None
    assert result["databaseAccessed"] is False and result["schemaValidated"] is False


def test_supplied_identity_manifest_matches_without_asserting_live_schema(tmp_path):
    record = sql(tmp_path, "005_auth_credentials.sql")
    sql(tmp_path, "006_auth_hardening.sql")
    result = inspect_plan(tmp_path, history(tmp_path, [record]))
    assert result["appliedFilesVerified"] == 1
    assert result["pendingFiles"] == ["006_auth_hardening.sql"]
    assert result["historyStatus"] == "supplied_manifest_matches_files"
    assert result["schemaValidated"] is False


def test_applied_team_005_is_not_silently_reassigned_to_auth_005(tmp_path):
    sql(tmp_path, "005_auth_credentials.sql")
    team = {"file": "005_user_logins.sql", "sha256": "a" * 64}
    with pytest.raises(MigrationPlanError, match="history_version_conflict:005"):
        inspect_plan(tmp_path, history(tmp_path, [team]))


def test_applied_sql_content_change_is_blocked(tmp_path):
    record = sql(tmp_path, "005_auth_credentials.sql")
    sql(tmp_path, "005_auth_credentials.sql", "ALTER TABLE example ADD COLUMN token TEXT;")
    with pytest.raises(MigrationPlanError, match="applied_migration_changed:005"):
        inspect_plan(tmp_path, history(tmp_path, [record]))


@pytest.mark.parametrize("records,reason", [
    ([{"file": "004_unknown.sql", "sha256": "a" * 64}], "history_migration_missing:004"),
    ([{"file": "../005_secret.sql", "sha256": "a" * 64}], "invalid_history_entry"),
    ([{"file": "005_auth_credentials.sql", "sha256": "bad"}], "invalid_history_entry"),
])
def test_unrecognized_or_malformed_history_fails_closed(tmp_path, records, reason):
    sql(tmp_path, "005_auth_credentials.sql")
    with pytest.raises(MigrationPlanError, match=reason):
        inspect_plan(tmp_path, history(tmp_path, records))


def test_duplicate_history_records_are_not_counted_twice(tmp_path):
    record = sql(tmp_path, "005_auth_credentials.sql")
    with pytest.raises(MigrationPlanError, match="duplicate_history_entry:005"):
        inspect_plan(tmp_path, history(tmp_path, [record, record]))


def test_dry_run_never_imports_database(monkeypatch, tmp_path, capsys):
    import builtins
    import apply_migrations
    actual_import = builtins.__import__
    def guarded(name, *args, **kwargs):
        if name == "database":
            pytest.fail("dry-run imported the database/environment")
        return actual_import(name, *args, **kwargs)
    monkeypatch.setattr(builtins, "__import__", guarded)
    monkeypatch.setattr(apply_migrations, "MIGRATIONS", tmp_path)
    monkeypatch.setattr(sys, "argv", ["apply_migrations.py", "--dry-run"])
    sql(tmp_path, "001_fixture.sql")
    apply_migrations.main()
    assert json.loads(capsys.readouterr().out)["databaseAccessed"] is False


def test_actual_apply_entry_blocks_duplicates_before_importing_database(monkeypatch, tmp_path):
    import builtins
    import apply_migrations
    actual_import = builtins.__import__
    def guarded(name, *args, **kwargs):
        if name == "database":
            pytest.fail("conflicting plan reached a DB connection")
        return actual_import(name, *args, **kwargs)
    monkeypatch.setattr(builtins, "__import__", guarded)
    monkeypatch.setattr(apply_migrations, "MIGRATIONS", tmp_path)
    monkeypatch.setattr(sys, "argv", ["apply_migrations.py"])
    sql(tmp_path, "005_auth_credentials.sql")
    sql(tmp_path, "005_user_logins.sql")
    with pytest.raises(SystemExit, match="duplicate_migration_version:005"):
        apply_migrations.main()


def test_history_argument_cannot_execute_sql(monkeypatch, tmp_path):
    import apply_migrations
    monkeypatch.setattr(sys, "argv", ["apply_migrations.py", "--history-file", str(tmp_path / "unread.json")])
    with pytest.raises(SystemExit) as stopped:
        apply_migrations.main()
    assert stopped.value.code == 2


def test_explicit_personal_lineage_cli_checks_only_its_preserved_history(monkeypatch, tmp_path, capsys):
    import apply_migrations
    root = tmp_path / "migrations"
    personal = root / "legacy_personal"
    personal.mkdir(parents=True)
    sql(root, "005_user_logins.sql")
    record = sql(personal, "005_auth_credentials.sql")
    sql(personal, "006_auth_hardening.sql")
    sql(personal, "007_user_logins.sql")
    history_path = history(tmp_path, [record])
    monkeypatch.setattr(apply_migrations, "MIGRATIONS", root)
    monkeypatch.setattr(sys, "argv", ["apply_migrations.py", "--lineage", "personal", "--dry-run",
                                     "--history-file", str(history_path)])
    apply_migrations.main()
    result = json.loads(capsys.readouterr().out)
    assert result["pendingFiles"] == ["006_auth_hardening.sql", "007_user_logins.sql"]
    assert result["databaseAccessed"] is False


def test_unknown_lineage_cannot_select_an_arbitrary_directory():
    import apply_migrations
    with pytest.raises(MigrationPlanError, match="unknown_migration_lineage"):
        apply_migrations.migration_directory("../another-project")
