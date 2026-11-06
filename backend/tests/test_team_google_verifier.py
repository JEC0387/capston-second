import os
import unittest
from unittest import mock
import auth_google

class VerifyTest(unittest.TestCase):
    def test_audience_and_expiry_checked(self):
        good = {'aud': 'web-client', 'iss': 'https://accounts.google.com', 'exp': '9999999999',
                'email_verified': 'true', 'sub': '123', 'email': 'x@example.com', 'name': 'X'}
        with mock.patch.dict(os.environ, {'GOOGLE_CLIENT_ID': 'web-client'}):
            with mock.patch.object(auth_google, '_tokeninfo', return_value=good):
                self.assertEqual('123', auth_google.verify('t')['sub'])
            for bad in ({'aud': 'other-app'}, {'exp': '1'}, {'iss': 'evil.example'}, {'email_verified': 'false'}):
                with mock.patch.object(auth_google, '_tokeninfo', return_value={**good, **bad}):
                    with self.assertRaises(auth_google.InvalidGoogleToken):
                        auth_google.verify('t')
        with mock.patch.dict(os.environ, {'GOOGLE_CLIENT_ID': ''}):
            with self.assertRaises(auth_google.GoogleNotConfigured):
                auth_google.verify('t')
