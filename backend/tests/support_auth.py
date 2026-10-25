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
