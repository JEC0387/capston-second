"""Manually issue a one-use legacy claim ticket after independent ownership verification.

Never use possession of the old device ID alone as verification. Run only against an
approved database; this command does not run during app startup or migration.
"""
import argparse

from auth_api import issue_legacy_ticket
from database import SessionLocal, engine


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--device-id", required=True)
    parser.add_argument("--verified-out-of-band", action="store_true")
    parser.add_argument("--hours", type=int, default=24)
    args = parser.parse_args()
    if not args.verified_out_of_band:
        parser.error("independent ownership verification is required")
    if engine is None:
        parser.error("database is not configured")
    with SessionLocal() as db:
        ticket = issue_legacy_ticket(db, args.device_id, args.hours)
    print(ticket)  # operator must deliver privately; never put it in an app log or Git


if __name__ == "__main__":
    main()
