"""Synthetic dotenv/startup checks; never read the developer's real .env or key."""
from pathlib import Path
import os
import runpy
import sys
from types import ModuleType

import pytest


ENTRY = Path(__file__).parents[1] / "outfit_vton_app.py"
KEYS = ("GENLOOK_API_KEY", "GENLOOK_ENABLED", "OUTFIT_VTON_ENABLED",
        "OUTFIT_VTON_PROVIDER", "GENLOOK_MAX_TOTAL_CREDITS")


@pytest.fixture
def isolated_entry(tmp_path, monkeypatch):
    # Copy only source, not live environment configuration. Stub both application
    # boundaries to prohibit DB initialization, real providers and listeners.
    monkeypatch.setattr(os, "environ", os.environ.copy())
    entry = tmp_path / "backend" / "outfit_vton_app.py"
    entry.parent.mkdir()
    entry.write_text(ENTRY.read_text(encoding="utf-8"), encoding="utf-8")
    for key in KEYS:
        monkeypatch.delenv(key, raising=False)
    monkeypatch.delenv("PYTHON_DOTENV_DISABLED", raising=False)
    calls = []
    provider = object()
    api_stub = ModuleType("outfit_vton_api")
    provider_stub = ModuleType("outfit_vton_provider")

    def configured_provider():
        calls.append(("provider", {key: os.environ.get(key) for key in KEYS}))
        return provider

    def create_app(**kwargs):
        assert kwargs == {"provider": provider}
        calls.append(("app", None))
        return "synthetic-app"

    api_stub.create_app = create_app
    provider_stub.configured_provider = configured_provider
    monkeypatch.setitem(sys.modules, "outfit_vton_api", api_stub)
    monkeypatch.setitem(sys.modules, "outfit_vton_provider", provider_stub)
    return entry, calls


def run(entry):
    assert runpy.run_path(str(entry))["app"] == "synthetic-app"


def test_exact_sibling_loaded_before_provider_and_not_cwd(isolated_entry, tmp_path, monkeypatch, capsys):
    entry, calls = isolated_entry
    env = entry.with_name(".env")
    original = b"GENLOOK_API_KEY=synthetic_file_only\nGENLOOK_ENABLED=false\nOUTFIT_VTON_PROVIDER=genlook\n"
    env.write_bytes(original)
    elsewhere = tmp_path / "unrelated"
    elsewhere.mkdir()
    (elsewhere / ".env").write_text("GENLOOK_API_KEY=synthetic_wrong_cwd\nGENLOOK_ENABLED=true\n")
    monkeypatch.chdir(elsewhere)
    run(entry)
    assert calls[0][0] == "provider" and calls[1][0] == "app"
    assert calls[0][1]["GENLOOK_API_KEY"] == "synthetic_file_only"
    assert calls[0][1]["GENLOOK_ENABLED"] == "false"
    assert calls[0][1]["OUTFIT_VTON_PROVIDER"] == "genlook"
    assert env.read_bytes() == original
    captured = capsys.readouterr()
    assert captured.out == "" and captured.err == ""


def test_process_settings_are_preserved_including_unrelated_values(isolated_entry, monkeypatch):
    entry, calls = isolated_entry
    entry.with_name(".env").write_text("GENLOOK_API_KEY=synthetic_file_only\nGENLOOK_MAX_TOTAL_CREDITS=3\n")
    monkeypatch.setenv("GENLOOK_API_KEY", "synthetic_process_only")
    monkeypatch.setenv("GENLOOK_MAX_TOTAL_CREDITS", "0")
    monkeypatch.setenv("P50_SYNTHETIC_UNRELATED", "preserve")
    run(entry)
    assert calls[0][1]["GENLOOK_API_KEY"] == "synthetic_process_only"
    assert calls[0][1]["GENLOOK_MAX_TOTAL_CREDITS"] == "0"
    assert os.environ["P50_SYNTHETIC_UNRELATED"] == "preserve"


def test_secret_text_is_not_interpolated(isolated_entry, monkeypatch):
    entry, calls = isolated_entry
    monkeypatch.setenv("P50_SYNTHETIC_EXPANSION", "must-not-expand")
    entry.with_name(".env").write_text('GENLOOK_API_KEY="${P50_SYNTHETIC_EXPANSION}"\n')
    run(entry)
    assert calls[0][1]["GENLOOK_API_KEY"] == "${P50_SYNTHETIC_EXPANSION}"


@pytest.mark.parametrize("content", [None, "GENLOOK_API_KEY=synthetic_file_only\n"])
def test_missing_file_or_key_alone_does_not_enable_calls(isolated_entry, content):
    entry, calls = isolated_entry
    if content is not None:
        entry.with_name(".env").write_text(content)
    run(entry)
    assert calls[0][1]["GENLOOK_ENABLED"] is None
    assert calls[0][1]["OUTFIT_VTON_ENABLED"] is None
    assert entry.with_name(".env").exists() == (content is not None)
