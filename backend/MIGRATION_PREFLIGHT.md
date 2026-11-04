# P30 migration collision preflight

The integration code keeps the existing Bearer authentication, account ownership checks,
legacy-claim approval, restore journal and client queue. Team sign-out/account-switch
code that deletes local data after a failed sync is **not imported**. The team
`X-Device-Id` authentication dependency is also **not imported**.

## Confirmed file collision; unknown deployed history

The reviewed team main is `411d008ebf542ec04c4966936303a686b4f784e5`.
These are SQL **declarations**, not a claim about deployed database contents:

| File | Declared changes |
| --- | --- |
| Backup `005_auth_credentials.sql` | `auth_credentials`: unique user/token/recovery hashes, data-enabled flag, user FK; `legacy_claim_tickets`: unique ticket hash, expiry/consumption time, user FK; supporting indexes. |
| Backup `006_auth_hardening.sql` | Durable rate-bucket table/index, independent legacy-claim approval metadata columns. |
| Team `005_user_logins.sql` | `user_logins`: user FK, provider/subject unique constraint, email/name and login timestamps, user index. |

Both `005` files could previously enter the same sorted execution list. Their table
names differ, but that does not reconcile applied migration history, account ownership,
or OAuth linkage. The runner now rejects duplicate numeric versions **before importing
the database module or opening a connection**. Do not resolve this by renaming one file
or replacing an already applied file without independent history and schema review.

No production history or database copy was supplied. No production DB was contacted.
No migration number, SQL file or schema was changed. SQL integration remains incomplete.

## Offline inspection

Run from `backend` with the existing Python environment:

```text
python apply_migrations.py --dry-run
python apply_migrations.py --dry-run --history-file reviewed-history.json
```

Dry-run imports no database settings, executes no SQL, and prints filenames and SHA-256
values only. Without history it reports `historyStatus=not_provided` and
`pendingFiles=null`; it does not infer that the database is empty or up to date.

An independently reviewed history export has this shape:

```json
{"version":1,"applied":[{"file":"005_auth_credentials.sql","sha256":"<64 lowercase hexadecimal characters from the applied file>"}]}
```

This is a format illustration, **not an actual applied-history record**. Do not generate
a manifest from the current checkout and call it historical evidence. A supplied manifest
is checked for unknown files, conflicting versions, changed file bytes, duplicate entries
and malformed data. Line-ending changes affect the byte checksum and require review;
the tool never silently normalizes a mismatch.

`supplied_manifest_matches_files` means only that the supplied identities match the
checkout. It does not verify the export's provenance, live schema, rows, constraints,
or execution success. `schemaValidated` remains false. `--history-file` is dry-run-only,
so inspecting a history export cannot accidentally execute migrations.

The ordinary apply command retains its prior behavior after the duplicate-number guard;
it is not a migration-history recorder. Do not run it on a production or unknown database
as part of P30. A separately approved integration still needs a verified history/export,
schema inventory, protected test copy and PostgreSQL transaction/constraint tests.

## Local evidence

- Baseline `migration_files()` returned both synthetic `005` files (2 entries).
- New duplicate guard blocks before database import, including the normal apply entry.
- 12 offline migration tests passed; three existing authentication/ownership/recovery
  tests passed in isolated SQLite fixtures (15 total in the focused invocation).
- These are not PostgreSQL migration-execution tests or proof of deployed data preservation.
- Existing PostgreSQL tests require a specifically configured local `fashionai_test`
  database. They were not run or represented as passing in this task.

Safe additional team feature candidates remain separate work. In particular the team's
`ProductRecommender` depends on its `OutfitSources`, `PreferredStyles`, `GenderFit` and
different `OutfitEngine` contracts. It was not copied merely to increase integration scope.
