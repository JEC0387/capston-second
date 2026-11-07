"""Separate, opt-in outfit service; importing this module does not enable generation.

Do not replace the production main application or its authentication routes.
Route /api/outfit-vton on the existing approved HTTPS/account origin to this
single-worker service only after the deployment and privacy review is complete.
"""
from pathlib import Path

from dotenv import load_dotenv

# Explicit sibling file only, before auth/provider settings are captured at import.
# Preserve deployment environment values; never expand ${...} inside a secret.
load_dotenv(Path(__file__).with_name(".env"), override=False, interpolate=False)

from outfit_vton_api import create_app
from outfit_vton_provider import configured_provider

app = create_app(provider=configured_provider())
