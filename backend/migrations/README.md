# Integration migration lineage

The root directory is the **team-main lineage**: original 001–005 (including
`005_user_logins.sql`), followed by new `006_auth_credentials.sql` and
`007_auth_hardening.sql`. These follow-ups add separate Bearer credentials and
approval/rate-limit tables; they do not reassign account ownership or delete data.

`legacy_personal/` preserves the personal branch's original 001–006 SQL bytes
and adds only `007_user_logins.sql`. Select it explicitly with
`--lineage personal` when the reviewed DB history contains personal 005/006.
It is deliberately outside the default team's non-recursive root scan.
`lineage_manifest.json` records immutable source identities, not live DB history.

Do not apply this root plan to a database whose history says
`005_auth_credentials.sql`. Preflight must reject that history rather than
rename an applied migration, edit its checksum, or invent a baseline. A reviewed
personal 001–006 history instead continues in its own lineage with only 007.
The two lineages produce the same required tables but retain distinct ledgers.
Do not switch lineages after application. Missing/unrecorded history still needs
independently reviewed baseline evidence; filenames or existing tables alone
are not proof that SQL ran. A checksum mismatch (including prior local byte/line
ending differences) is never automatically normalized or accepted.

Run `python apply_migrations.py --dry-run` for offline inspection. An existing
team DB needs independently exported history and schema review before any
execution. The runner still permits only an explicitly approved localhost
`fashionai_test` DB and `p31_migration_test_<32 hex>` schema. Production execution
is not enabled. No application startup applies these SQL files automatically.

Personal-lineage preflight (DB-free):
`python apply_migrations.py --lineage personal --dry-run --history-file <reviewed-history.json>`.
For a separately approved isolated DB only, the same `--lineage personal` works
with the existing `--apply-local-test --review-file <review.json> --schema
p31_migration_test_<32 hex> --confirm-local-test` options. The full-plan review,
actual ledger prefix, schema and localhost checks are unchanged.

Team social identity records remain, but OAuth entry points are unavailable
until a secure identity-to-Bearer bridge is verified. X-Device-Id is not proof of
ownership. Provider environment variables alone do not enable the old routes.
