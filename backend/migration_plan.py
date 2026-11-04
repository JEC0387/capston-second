"""Offline migration preflight. Never imports the database or executes SQL.

An operator may supply an independently exported/reviewed history manifest:
{"version": 1, "applied": [{"file": "001_example.sql", "sha256": "..."}]}
Matching this manifest verifies file identity only, not the live schema or history's provenance.
"""
import hashlib
import json
from pathlib import Path
import re


NAME = re.compile(r"([0-9]{3})_[A-Za-z0-9_]+\.sql\Z")
DIGEST = re.compile(r"[a-f0-9]{64}\Z")


class MigrationPlanError(ValueError):
    pass


def migration_files(directory: Path) -> list[Path]:
    files = sorted(directory.glob("[0-9][0-9][0-9]_*.sql"))
    versions = set()
    for file in files:
        match = NAME.fullmatch(file.name)
        if match is None or not file.is_file():
            raise MigrationPlanError("invalid_migration_filename")
        version = match[1]
        if version in versions:
            # Report the public migration number only, never DB settings or SQL contents.
            raise MigrationPlanError(f"duplicate_migration_version:{version}")
        versions.add(version)
    return files


def inspect_plan(directory: Path, history_file: Path | None = None) -> dict:
    files = migration_files(directory)
    manifest = [{"file": file.name, "sha256": hashlib.sha256(file.read_bytes()).hexdigest()}
                for file in files]
    result = {"version": 1, "files": manifest, "historyStatus": "not_provided",
              "databaseAccessed": False, "schemaValidated": False,
              "appliedFilesVerified": 0, "pendingFiles": None}
    if history_file is None:
        return result
    try:
        if history_file.stat().st_size > 1024 * 1024:
            raise MigrationPlanError("history_manifest_too_large")
        history = json.loads(history_file.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError):
        raise MigrationPlanError("history_manifest_unreadable") from None
    if not isinstance(history, dict) or history.get("version") != 1 or not isinstance(history.get("applied"), list):
        raise MigrationPlanError("invalid_history_manifest")
    known = {item["file"]: item["sha256"] for item in manifest}
    by_version = {NAME.fullmatch(name)[1]: name for name in known}
    applied = set()
    for item in history["applied"]:
        if not isinstance(item, dict):
            raise MigrationPlanError("invalid_history_entry")
        name, digest = item.get("file"), item.get("sha256")
        if not isinstance(name, str) or not NAME.fullmatch(name) or not isinstance(digest, str) or not DIGEST.fullmatch(digest):
            raise MigrationPlanError("invalid_history_entry")
        version = NAME.fullmatch(name)[1]
        if name in applied:
            raise MigrationPlanError(f"duplicate_history_entry:{version}")
        if name not in known:
            reason = "history_version_conflict" if version in by_version else "history_migration_missing"
            raise MigrationPlanError(f"{reason}:{version}")
        if known[name] != digest:
            raise MigrationPlanError(f"applied_migration_changed:{version}")
        applied.add(name)
    result.update(historyStatus="supplied_manifest_matches_files",
                  appliedFilesVerified=len(applied),
                  pendingFiles=[item["file"] for item in manifest if item["file"] not in applied])
    return result
