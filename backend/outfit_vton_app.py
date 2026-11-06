"""Separate, opt-in GPU service; importing this module does not enable generation.

Do not replace the production main application or its authentication routes.
Route /api/outfit-vton on the existing approved HTTPS/account origin to this
single-worker service only after the deployment and privacy review is complete.
"""
from outfit_vton_api import create_app
from outfit_vton_provider import configured_provider

app = create_app(provider=configured_provider())
