"""구글 로그인 — 구글 확인은 가짜로 바꾸고(네트워크 없음), 계정 연결 · 기록 이어 받기를 실제 SQLite 로 확인."""
import os
os.environ['DATABASE_URL'] = ''
os.environ['DB_AUTO_CREATE'] = 'false'

import unittest
from unittest import mock
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
import auth_google
import main
from database import Base, get_db, reset_schema_cache

ACCOUNTS = {
    'token-a': {'sub': 'google-sub-a', 'email': 'a@example.com', 'name': '계정A', 'picture': ''},
    'token-b': {'sub': 'google-sub-b', 'email': 'b@example.com', 'name': '계정B', 'picture': ''},
}


def fake_verify(token):
    if token not in ACCOUNTS:
        raise auth_google.InvalidGoogleToken()
    return ACCOUNTS[token]


class GoogleLoginTest(unittest.TestCase):
    def setUp(self):
        reset_schema_cache()
        self.engine = create_engine('sqlite://', connect_args={'check_same_thread': False}, poolclass=StaticPool)
        Base.metadata.create_all(self.engine)
        factory = sessionmaker(bind=self.engine, expire_on_commit=False)

        def test_db():
            with factory() as session:
                yield session
        main.app.dependency_overrides[get_db] = test_db
        self.client = TestClient(main.app)
        self.patch = mock.patch.object(auth_google, 'verify', side_effect=fake_verify)
        self.patch.start()

    def tearDown(self):
        self.patch.stop()
        self.client.close()
        main.app.dependency_overrides.clear()
        self.engine.dispose()

    def login(self, token, device):
        return self.client.post('/api/auth/google', json={'idToken': token}, headers={'X-Device-Id': device})

    def wardrobe_count(self, device):
        return self.client.get('/api/profile', headers={'X-Device-Id': device}).json()['wardrobeCount']

    def add_clothes(self, device, ids):
        items = [{'id': i, 'name': i, 'category': '상의', 'colorName': '블랙', 'colorValue': 0, 'addedAt': 1}
                 for i in ids]
        r = self.client.post('/api/wardrobe/sync', json={'items': items}, headers={'X-Device-Id': device})
        self.assertEqual(200, r.status_code, r.text)

    def test_first_login_keeps_this_phone_data(self):
        self.add_clothes('phone-1', ['shirt', 'pants'])
        r = self.login('token-a', 'phone-1')
        self.assertEqual(200, r.status_code, r.text)
        body = r.json()
        self.assertTrue(body['isNewAccount'])
        self.assertFalse(body['switched'])
        self.assertEqual('phone-1', body['accountKey'])
        self.assertEqual('a@example.com', body['email'])
        self.assertEqual('계정A', body['name'])
        self.assertEqual(2, self.wardrobe_count('phone-1'))

    def test_same_account_on_another_phone_gets_same_data(self):
        self.add_clothes('phone-1', ['shirt', 'pants'])
        self.login('token-a', 'phone-1')
        r = self.login('token-a', 'phone-2')
        self.assertFalse(r.json()['isNewAccount'])
        self.assertTrue(r.json()['switched'])
        self.assertEqual('phone-1', r.json()['accountKey'])     # 앱은 이 번호로 바꿔 계정 기록을 받아 온다

    def test_different_accounts_are_separate(self):
        self.add_clothes('phone-1', ['shirt'])
        a = self.login('token-a', 'phone-1').json()
        # 로그아웃하면 앱은 새 기기 번호를 만든다 → 계정 B 는 새 사용자
        b = self.login('token-b', 'phone-1-after-logout').json()
        self.assertNotEqual(a['accountKey'], b['accountKey'])
        self.assertEqual(0, self.wardrobe_count(b['accountKey']))
        self.assertEqual(1, self.wardrobe_count(a['accountKey']))

    def test_phone_already_linked_does_not_merge_into_other_account(self):
        self.add_clothes('phone-1', ['shirt'])
        self.login('token-a', 'phone-1')
        b = self.login('token-b', 'phone-1').json()             # 로그아웃 없이 다른 계정
        self.assertTrue(b['isNewAccount'])
        self.assertTrue(b['switched'])
        self.assertNotEqual('phone-1', b['accountKey'])
        self.assertEqual(0, self.wardrobe_count(b['accountKey']))

    def test_invalid_token_and_missing_config(self):
        self.assertEqual(401, self.login('forged', 'phone-1').status_code)
        self.patch.stop()
        with mock.patch.object(auth_google, 'verify', side_effect=auth_google.GoogleNotConfigured()):
            self.assertEqual(503, self.login('token-a', 'phone-1').status_code)
        self.patch.start()

    def test_delete_my_data_removes_login(self):
        self.login('token-a', 'phone-1')
        self.assertEqual(200, self.client.delete('/api/me', headers={'X-Device-Id': 'phone-1'}).status_code)
        r = self.login('token-a', 'phone-3').json()
        self.assertTrue(r['isNewAccount'])


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


if __name__ == '__main__':
    unittest.main()
