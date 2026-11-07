# P31 migration execution protection (P30 preflight preserved)

## Compatibility change: the old apply command now fails closed

`python apply_migrations.py` no longer imports `database.py`, loads `.env`, reads
`DATABASE_URL`, or applies SQL. Previously it enabled autocommit, executed every
file on every invocation and could permanently commit earlier files before a later
file failed. P30's duplicate-number check did not resolve those risks.

Operators who used the old command must first establish independently reviewed
schema/history and an approved **local test copy**. There is intentionally no
production apply mode or flag to bypass history checking in P31. This is a runner
compatibility change, not a change to the running Android app or database schema.
The DB-free `--dry-run` and `--dry-run --history-file` interfaces remain available.

## Implemented runner; actual PostgreSQL execution still unverified

- A reviewed execution manifest pins **all** source SQL filenames, order and exact
  byte SHA-256 values, including pending files. The reviewed applied subset must be
  a prefix, not an inferred list of tables or `IF NOT EXISTS` effects.
- Existing SQL files are not edited. The runner lexes strings/comments and accepts
  only one outer `BEGIN; ... COMMIT;` envelope; those two statements are not sent to
  PostgreSQL. It uses one transaction for the complete pending batch and its ledger.
- Supported SQL is deliberately limited to current project DDL: unqualified table
  creation, simple column additions, built-in column types, constant/`now()` defaults,
  inline keys/references, named unique constraints and simple column indexes.
  Procedural SQL, arbitrary/default/check/generated functions, custom types,
  qualified targets/references, concurrent indexes, session/transaction statements,
  destructive alterations, quoted identifiers and dollar quoting are rejected.
  This is **not a general SQL sandbox** or a guarantee about arbitrary imported SQL.
- An existing approved schema named `p31_migration_test_<32 lowercase hex>` is
  required. No schema/database is created, dropped or chosen automatically. The
  transaction search path contains only that schema (no `public` fallback), and the
  checksum ledger is explicitly schema-qualified. PostgreSQL's built-in catalog
  remains implicitly searched for built-in types and functions.
- A transaction advisory lock serializes cooperating runners for the same schema.
  A busy lock fails instead of waiting/retrying indefinitely. Statement and lock
  timeouts bound the execution. [PostgreSQL advisory-lock documentation](https://www.postgresql.org/docs/current/explicit-locking.html#ADVISORY-LOCKS).
- `_fashionai_migration_history` records version, file, SHA-256, origin and timestamp.
  The first ledger can import only the explicitly reviewed historical prefix,
  marked `reviewed_baseline`; files executed by this runner are marked `executed`.
  An existing ledger must match the current plan prefix. Changed, missing, unknown,
  reordered or regressed entries block before migration DDL; completed files skip.
- DDL and new ledger entries commit together. An execution failure requests rollback.
  A lost commit acknowledgement is reported as **outcome unknown**, never as proven
  rollback or success; reconnect and inspect the ledger before retry. The next run
  reads committed history before deciding pending work. [PostgreSQL transactions](https://www.postgresql.org/docs/current/tutorial-transactions.html).
- Driver exception text, URL credentials, SQL text and row data are not printed.

The historical migration files are **not a complete empty-database bootstrap**.
`001` expects `body_measurements`; subsequent files expect `users`, `clothes` and
`products`. An independently reviewed copy of the prerequisite schema is required.
Creating today's ORM metadata does not prove which historical migrations ran and
must not be automatically recorded as applied history.

## Explicit local-test execution — only after separate operator approval

P31 found no configured `TEST_POSTGRES_URL` or approved isolated database copy.
No SQL below was executed against PostgreSQL in this task. Do not point these steps
at Supabase, Railway, production or a port-forwarded production service.

1. Obtain approval and an isolated local `fashionai_test` database with a reviewed
   existing test schema/copy and verified prerequisite tables. Keep private data out
   of fixtures; no production copy is automatically made by the tool.
2. Independently examine actual migration history and schema/constraints. Resolve
   team `005_user_logins.sql` versus backup `005_auth_credentials.sql` before choosing
   any new version. Do not rename/copy them to make a duplicate-number check pass.
3. Prepare a private execution-review JSON with this shape. Placeholders are not
   usable values; do not turn a fresh checkout manifest into fake historical proof:

```json
{
  "version": 1,
  "files": [{"file": "001_example.sql", "sha256": "<reviewed exact file SHA-256>"}],
  "applied": [],
  "schemaReview": {
    "schema": "p31_migration_test_<32 lowercase hex>",
    "approved": true,
    "reference": "<independent non-personal review reference>"
  }
}
```

`files` contains the full ordered plan; `applied` contains the independently verified
applied prefix using the same file/hash records. `approved` is an operator assertion,
**not automated schema verification or authorization from this document**. An empty
`applied` list must not be used merely because a previous ledger is missing.

4. Set `TEST_POSTGRES_URL` privately in the process environment. Only `postgresql://`
   or `postgres://`, loopback hostname, database `fashionai_test`, explicit username,
   valid port and no query/fragment/options are accepted. Validated fields are passed
   separately to the driver; `.env` and `DATABASE_URL` are never fallback sources.
   Localhost naming alone does not establish operator approval or schema provenance.
5. From `backend`, after approval, run:

```text
python apply_migrations.py --apply-local-test --confirm-local-test --schema p31_migration_test_<32 lowercase hex> --review-file <private-reviewed-execution.json>
```

6. Confirm actual PostgreSQL DDL, ledger, no-op rerun, schema constraints and a
   deliberately failing follow-up migration in a disposable test copy. Verify no
   partially applied batch. Only then design/review a later production deployment;
   P31's CLI does not provide one.

## P31 evidence and limitations

- Offline tests: **58 passed** (12 existing preflight + 46 execution-contract cases).
- Tests verify the unchanged six SQL files against the restricted grammar; fake
  DBAPI transactions test ledger/rollback/unknown-commit behavior and failure guards.
- These tests do **not execute SQL** and are not PostgreSQL syntax, constraint,
  locking/concurrency, durability, actual rollback or deployed-data validation.
- Original six SQL files/versions, auth ownership, Android journals/queues and team
  main are unchanged. No new `007` migration is fabricated without actual history.
- Actual team SQL integration and PostgreSQL execution remain incomplete pending
  the approved copy and independently established history/schema review.

## P30 findings retained

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

P30's ordinary apply command previously retained the unsafe all-files execution behavior.
P31 replaces that entry with the explicit local-test-only runner described above. A
separately approved integration still needs a verified history/export, schema inventory,
protected test copy and real PostgreSQL transaction/constraint tests.

## Earlier P30 local evidence (not added to P31 totals)

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
