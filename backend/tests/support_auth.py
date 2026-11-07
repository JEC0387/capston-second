"""Test-only account issuance against each test's isolated SQLite database."""
import os
from unittest import mock


class AuthHeaders:
    def __init__(self, client):
        self.client = client
        self.accounts = {}

    def __call__(self, label="device-a"):
        if label not in self.accounts:
            with mock.patch.dict(os.environ, {"AUTH_REGISTRATION_ENABLED": "true"}):
                response = self.client.post("/api/auth/register", json={})
            assert response.status_code == 201, response.text
            self.accounts[label] = response.json()["accessToken"]
        return {"Authorization": "Bearer " + self.accounts[label]}


def operator_get(client, path):
    """Exercise real operator authentication using an ephemeral test-only secret."""
    import hashlib
    secret = "test-operator-secret-" + "A" * 32
    with mock.patch.dict(os.environ, {"ANALYTICS_ADMIN_TOKEN_SHA256": hashlib.sha256(secret.encode()).hexdigest()}):
        return client.get(path, headers={"Authorization": "Bearer " + secret})
