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
import auth_social
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


class SocialLoginTest(GoogleLoginTest):
    """카카오 · 페이스북 · Apple — 각 회사 서버 호출만 가짜로 바꾸고 시작 → 콜백 → 교환 흐름 전체를 확인"""
    ENV = {'KAKAO_REST_API_KEY': 'kakao-key', 'FACEBOOK_APP_ID': 'fb-id', 'FACEBOOK_APP_SECRET': 'fb-secret',
           'APPLE_SERVICE_ID': 'com.example.signin', 'APPLE_TEAM_ID': 'TEAM', 'APPLE_KEY_ID': 'KEY',
           'APPLE_PRIVATE_KEY': 'x', 'PUBLIC_BASE_URL': 'https://server.example'}

    def setUp(self):
        super().setUp()
        self.env = mock.patch.dict(os.environ, self.ENV)
        self.env.start()
        users = {'kakao-code': {'sub': '1001', 'email': 'k@example.com', 'name': '카카오유저'},
                 'fb-code': {'sub': '2002', 'email': '', 'name': '페북유저'},
                 'apple-code': {'sub': 'apple.3003', 'email': 'relay@privaterelay.appleid.com', 'name': ''}}

        def fake_fetch(provider, code, uri, user_json=None):
            self.assertEqual(f'https://server.example/api/auth/{provider}/callback', uri)
            if code not in users:
                raise auth_social.ProviderFailed('bad')
            return users[code]
        self.fetch = mock.patch.object(auth_social, 'fetch_user', side_effect=fake_fetch)
        self.fetch.start()

    def tearDown(self):
        self.fetch.stop()
        self.env.stop()
        super().tearDown()

    def start_login(self, provider, device):
        r = self.client.post('/api/auth/start', json={'provider': provider}, headers={'X-Device-Id': device})
        self.assertEqual(200, r.status_code, r.text)
        url = r.json()['url']
        from urllib.parse import urlparse, parse_qs
        return url, parse_qs(urlparse(url).query)['state'][0]

    def code_from(self, html):
        import re
        m = re.search(r'fashionai://login\?code=([A-Za-z0-9_-]+)', html)
        return m.group(1) if m else None

    def test_providers_reflect_server_config(self):
        got = self.client.get('/api/auth/providers').json()
        self.assertEqual({'google': False, 'kakao': True, 'facebook': True, 'apple': True}, got)

    def test_kakao_flow_links_this_phone(self):
        self.add_clothes('phone-k', ['shirt'])
        url, state = self.start_login('kakao', 'phone-k')
        self.assertTrue(url.startswith('https://kauth.kakao.com/oauth/authorize?'))
        html = self.client.get('/api/auth/kakao/callback', params={'code': 'kakao-code', 'state': state}).text
        code = self.code_from(html)
        self.assertIsNotNone(code)
        r = self.client.post('/api/auth/exchange', json={'code': code}).json()
        self.assertEqual('kakao', r['provider'])
        self.assertEqual('phone-k', r['accountKey'])
        self.assertTrue(r['isNewAccount']); self.assertFalse(r['switched'])
        self.assertEqual('카카오유저', r['name'])
        # 같은 코드는 한 번만
        self.assertEqual(401, self.client.post('/api/auth/exchange', json={'code': code}).status_code)

    def test_facebook_same_account_on_other_phone(self):
        _, s1 = self.start_login('facebook', 'phone-f1')
        c1 = self.code_from(self.client.get('/api/auth/facebook/callback', params={'code': 'fb-code', 'state': s1}).text)
        first = self.client.post('/api/auth/exchange', json={'code': c1}).json()
        _, s2 = self.start_login('facebook', 'phone-f2')
        c2 = self.code_from(self.client.get('/api/auth/facebook/callback', params={'code': 'fb-code', 'state': s2}).text)
        second = self.client.post('/api/auth/exchange', json={'code': c2}).json()
        self.assertEqual(first['accountKey'], second['accountKey'])
        self.assertTrue(second['switched']); self.assertFalse(second['isNewAccount'])

    def test_apple_posts_back(self):
        url, state = self.start_login('apple', 'phone-a')
        self.assertIn('response_mode=form_post', url)
        html = self.client.post('/api/auth/apple/callback', data={'code': 'apple-code', 'state': state}).text
        r = self.client.post('/api/auth/exchange', json={'code': self.code_from(html)}).json()
        self.assertEqual('apple', r['provider'])

    def test_forged_state_cancel_and_unknown_provider(self):
        html = self.client.get('/api/auth/kakao/callback', params={'code': 'kakao-code', 'state': 'forged'}).text
        self.assertIsNone(self.code_from(html))
        self.assertIn('error=failed', html)
        _, state = self.start_login('kakao', 'phone-k')
        html = self.client.get('/api/auth/kakao/callback', params={'error': 'access_denied', 'state': state}).text
        self.assertIn('error=cancelled', html)
        self.assertEqual(404, self.client.get('/api/auth/naver/callback').status_code)
        self.assertEqual(422, self.client.post('/api/auth/start', json={'provider': 'naver'}).status_code)

    def test_kakao_and_google_accounts_are_separate(self):
        g = self.login('token-a', 'phone-x').json()
        _, state = self.start_login('kakao', 'phone-y')
        code = self.code_from(self.client.get('/api/auth/kakao/callback', params={'code': 'kakao-code', 'state': state}).text)
        k = self.client.post('/api/auth/exchange', json={'code': code}).json()
        self.assertNotEqual(g['accountKey'], k['accountKey'])

    def test_not_configured(self):
        with mock.patch.dict(os.environ, {'KAKAO_REST_API_KEY': ''}):
            self.assertEqual(503, self.client.post('/api/auth/start', json={'provider': 'kakao'},
                                                   headers={'X-Device-Id': 'p'}).status_code)


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
