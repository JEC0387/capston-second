"""Exercise the optional PostgreSQL fixture's target boundary without a driver."""
import pytest

from migration_plan import MigrationPlanError
from tests import test_auth_postgres as postgres_tests


@pytest.mark.parametrize("target", [
    "postgresql://synthetic:unused@127.0.0.1:15453/fashionai_test?host=example.invalid",
    "postgresql://synthetic:unused@127.0.0.1:15453/fashionai_test?hostaddr=192.0.2.1",
    "postgresql://synthetic:unused@127.0.0.1:15453/fashionai_test?dbname=production",
    "postgresql://synthetic:unused@127.0.0.1:15453/fashionai_test?options=-csearch_path%3Dpublic",
    "postgresql://synthetic:unused@127.0.0.1:15453/fashionai_test?port=5433",
    "postgresql://synthetic:unused@127.0.0.1:15453/fashionai_test#ignored",
    "postgresql://synthetic:unused@example.invalid/fashionai_test",
    "postgresql://synthetic:unused@127.0.0.1/production",
], ids=["host_override", "hostaddr_override", "database_override", "options_override",
        "port_override", "fragment", "nonlocal", "wrong_database"])
def test_untrusted_test_target_is_rejected_before_engine_or_driver(monkeypatch, target):
    monkeypatch.setenv("TEST_POSTGRES_URL", target)
    calls = []
    def forbidden_engine(*args, **kwargs):
        calls.append(True)
        raise AssertionError("Unvalidated test target reached engine creation")
    monkeypatch.setattr(postgres_tests, "create_engine", forbidden_engine)
    fixture = postgres_tests.postgres.__wrapped__(monkeypatch)
    try:
        with pytest.raises(MigrationPlanError, match="explicit_local_test_postgres_url_required"):
            next(fixture)
        assert calls == []
    finally:
        fixture.close()


def test_approved_local_target_is_rebuilt_from_checked_fields_not_raw_url(monkeypatch):
    monkeypatch.setenv("TEST_POSTGRES_URL", "postgresql://synthetic:p%40ss@127.0.0.1:15453/fashionai_test")
    class ReachedValidatedEngine(Exception):
        pass
    def inspect_engine(url, **kwargs):
        assert url.drivername == "postgresql+psycopg2"
        assert url.host == "127.0.0.1" and url.port == 15453
        assert url.database == "fashionai_test" and url.username == "synthetic"
        assert url.password == "p@ss"
        assert dict(url.query) == {}
        assert kwargs["connect_args"]["connect_timeout"] == 5
        raise ReachedValidatedEngine()
    monkeypatch.setattr(postgres_tests, "create_engine", inspect_engine)
    fixture = postgres_tests.postgres.__wrapped__(monkeypatch)
    try:
        with pytest.raises(ReachedValidatedEngine):
            next(fixture)
    finally:
        fixture.close()
