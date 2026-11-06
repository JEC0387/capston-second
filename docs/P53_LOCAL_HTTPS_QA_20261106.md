# P53 continuation — real local QA server and S25+ HTTPS acceptance

## Latest result, 2026-11-06 KST

- Isolated PostgreSQL/TLS/authentication: PASS, actually running on the development PC.
- Public HTTPS relay and restricted routes: PASS for the approved connection test.
- S25+ QA18608: invalid-token rejection and valid-token config parsing PASS.
- Tunnel shutdown: PASS; cloudflared stopped and prior public endpoint returned530.
- Data:36 same,0 changed/missing/new relative to the initial P53 post-use baseline.
- Genlook: disabled throughout;0uploads/0generations/0credits spent. Last known6
  credits is still P51's observation; this run did not query vendor credits.
- No new APK/install, operating-system/phone trust changes, CI, Push or team merge.
- Actual new AI image / compare-save-reopen E2E: NOT TESTED. Release remains NO-GO.

## What was actually created

Private root (Git-ignored and current-Windows-user ACL restricted):
`fashion_app/build/p53-private/local-server-01` in the existing worktree.
No passwords, QA bearer tokens, certificate private keys, or tunnel URL are included
in this document or public logs. The actual private runtime config stays there.

1. Official `postgres:16-alpine`, pull digest
   `sha256:aa6cb33cf4c19c765cefcf48d96176258800870a9a1e930ad126200f9d825139`.
   Immutable local image ID is recorded in `DB_RESOURCE.json`.
2. New container `fashionai-p53-qa-db-20261106`, new independently owned volume
   `fashionai-p53-qa-db-20261106-data`;256MiB limit,1CPU, restart=no.
   Only `127.0.0.1:15453` is published. SCRAM passwords and PostgreSQL TLS enabled.
   No existing volume/schema was modified. The separately approved week2 container
   auto-started with Docker, then was normally stopped again; never deleted.
3. Explicitly checked new empty `fashionai_p53_qa`, then created existing ORM tables
   and two synthetic QA users/hashed credentials there. This is a fresh QA schema,
   not a migration test or production integration. Existing `auth_api.current_user`
   is used unchanged; no dependency override/mock identity/X-Device-Id bypass.
4. Existing `outfit_vton_app:app`, loopback TLS `127.0.0.1:18453`, one worker, no
   access logs/proxy-header trust. Environment forces a local DB URL, empty Genlook
   key and `GENLOOK_ENABLED=false`, so the sibling production-like `.env` cannot
   activate vendor calls. Existing backend `.env` was not edited.
5. Dedicated TLS read-only gateway `127.0.0.1:18454`. Only GET config and bounded
   UUID job/result paths are forwarded to that fixed local TLS backend. Other paths,
   docs/admin/query strings and mutation methods are rejected. Only Authorization
   is forwarded; redirects disabled, TLS verified, response/error logging sanitized.
6. Official Cloudflare binary downloaded, verified against official GitHub asset
   SHA256 (`CLOUDFLARED_SOURCE.json`). Python's GitHub CA-chain failure was not bypassed:
   Windows native validated HTTPS was used successfully instead. No global tool install.
7. Explicitly approved Quick Tunnel targeted only the read-only gateway. Origin TLS
   verified against the local certificate; PostgreSQL never exposed. Tunnel stderr
   was parsed in memory, not logged; URL saved only in restricted private config.
   No Cloudflare account, payment, root-certificate installation or personal input.

Local TLS certificates have a three-day lifetime and localhost/IP SANs. The local
client explicitly trusts this test certificate and verifies hostname; this is not
a publicly trusted origin certificate or a change to device trust. Public TLS was
provided by Cloudflare and accepted by the actual unchanged Android app.

## Actual validation evidence

| Evidence | Result |
|---|---|
| LOCAL_HTTPS_AUTH_VERIFICATION.json | Local verified TLS: missing401, invalid401, QA account200 ready=false/genlook_disabled, unknown job404 |
| GATEWAY_VERIFICATION.json | docs/openapi404, jobsPOST404, configPOST405, missing401, valid200 |
| PUBLIC_GATEWAY_VERIFICATION.json | Public HTTPS: docs404, missing401, invalid401, valid200 |
| DEVICE_INVALID_AUTH.json | Actual S25+ invalid-token authentication error displayed |
| DEVICE_CONFIG_PARSED.json | Actual S25+ valid-token config: Genlook disclosure + clothing-pair-then-shoes + not-ready state displayed |
| TUNNEL_CLOSED.json | Exact cloudflared binary/PID stopped; previous URL530; endpoint no longer reaches QA |
| AFTER_CONNECTION_DATA_AUDIT.json |36/36 unchanged; QA18608/Preview18605 versions retained |

The initial DEVICE_VALID_AUTH observation did not see the detailed disabled notice
because it was below the viewport; it is not proof of a failure or a displayed line.
The subsequent config-parsing evidence records only actually visible server-derived
labels. Wrong-owner access to an existing generated job was not tested: no job was
created. Unknown-job404 is not reported as that broader ownership test.

Automation helper errors were contained: Windows default text encoding was corrected
before UI execution; the settings panel retains the base URL, so the helper was
corrected to replace the full field instead of appending. A final real valid-token
config fetch passed. Neither issue changed application code or caused generation.

On the phone, QA connection was explicitly cleared through its existing RAM-only
button and the panel closed. Existing job records/files were retained. Raw photo,
token and server URL were not placed in Git, APK, access logs or published reports.

## Current resource state and next step

Cloudflared is STOPPED. Local DB/FastAPI/read-only gateway are retained on loopback
for the next approved stage; they are not public after relay shutdown. Secrets,
certificate and private scripts are retained, not deleted. `prepare_local.py` refuses
a nonempty DB; do not rerun it against the initialized database or reuse it for an
operating DB. Read private process records and verify process identity before any
restart/stop. Preserve existing volume, credentials and generation journals.

The existing Railway deployment configuration was read, not changed. Current public
unauthenticated GETs to `/api/auth/status` and `/api/outfit-vton/config` both returned
404. No team-service admin access or separate available team QA server was established.

Next required engineering work is the intermediate clothing-result review gate:
persist job/input/result identity, pause after pair output, explicitly approve or
reject before shoes, retain reserved credits and avoid POST replay on timeout or
restart. The existing provider currently auto-advances, so it must not be enabled
under P53's visual-review condition yet. The new read-only gateway deliberately
cannot submit jobs. Its public mutation policy must be reviewed for the next stage.

The user's bounded Genlook test approval is preserved. This latest phase explicitly
limited work to connectivity/access control with generation disabled; no credit
permission was consumed. Future generation must revalidate current input rights,
retention/deletion, actual balance and the3-credit ledger, and complete actual S25+
generation/quality/comparison/save/reopen before any GO statement.

Sources: [Docker official PostgreSQL image](https://hub.docker.com/_/postgres),
[Docker Desktop start](https://docs.docker.com/reference/cli/docker/desktop/start/),
[Cloudflare Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/).
Quick Tunnels are development/testing infrastructure, with no uptime guarantee;
they are not a production deployment or privacy certification.
