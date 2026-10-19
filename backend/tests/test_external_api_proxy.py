"""서버가 대신 부르는 외부 API(네이버 이미지 검색 · 날씨) — 실제 네이버 · OpenWeatherMap 에는 접속하지 않는다."""
import os
os.environ['DATABASE_URL'] = ''
os.environ['DB_AUTO_CREATE'] = 'false'

import io
import json
import unittest
from unittest import mock

from fastapi.testclient import TestClient

import main
import naver
import weather


def _fake_urlopen(payload: dict, calls: list):
    def opener(req, timeout=None):
        calls.append(req)
        return io.BytesIO(json.dumps(payload).encode('utf-8'))
    return opener


class ExternalApiProxyTest(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(main.app)
        naver._cache.clear()
        weather._forecast_cache.clear()

    def tearDown(self):
        self.client.close()

    def test_without_keys_server_says_so_instead_of_fake_values(self):
        with mock.patch.object(weather, 'OWM_API_KEY', ''), \
                mock.patch.object(naver, 'NAVER_API_KEY_ID', ''), mock.patch.object(naver, 'NAVER_API_KEY', ''):
            self.assertEqual(503, self.client.get('/api/weather').status_code)
            self.assertEqual(503, self.client.get('/api/weather/forecast').status_code)
            self.assertEqual(503, self.client.get('/api/naver/image-search', params={'query': '셔츠'}).status_code)

    def test_image_search_validates_query(self):
        self.assertEqual(422, self.client.get('/api/naver/image-search').status_code)
        self.assertEqual(422, self.client.get('/api/naver/image-search', params={'query': 'a', 'display': 500}).status_code)

    def test_image_search_maps_items_and_reuses_same_query(self):
        calls = []
        payload = {'items': [{'title': '<b>셔츠</b> 코디', 'link': 'https://img/a.jpg', 'thumbnail': 'https://img/a_s.jpg'}]}
        with mock.patch.object(naver, 'NAVER_API_KEY_ID', 'id'), mock.patch.object(naver, 'NAVER_API_KEY', 'key'), \
                mock.patch.object(naver.urllib.request, 'urlopen', _fake_urlopen(payload, calls)):
            for _ in range(2):
                r = self.client.get('/api/naver/image-search', params={'query': '셔츠', 'display': 3})
                self.assertEqual(200, r.status_code, r.text)
                self.assertEqual([{'title': '셔츠 코디', 'imageUrl': 'https://img/a.jpg',
                                   'thumbnailUrl': 'https://img/a_s.jpg'}], r.json()['items'])
        self.assertEqual(1, len(calls))   # 두 번째는 기억해 둔 결과
        self.assertEqual('key', calls[0].get_header('X-ncp-apigw-api-key'))

    def test_forecast_is_passed_through_for_app_parser(self):
        calls = []
        payload = {'city': {'timezone': 32400}, 'list': [{'dt': 1, 'main': {'temp': 18}}]}
        with mock.patch.object(weather, 'OWM_API_KEY', 'key'), \
                mock.patch.object(weather.urllib.request, 'urlopen', _fake_urlopen(payload, calls)):
            for _ in range(2):
                r = self.client.get('/api/weather/forecast', params={'lat': 37.57, 'lon': 126.98})
                self.assertEqual(200, r.status_code, r.text)
                self.assertEqual(payload, r.json())
        self.assertEqual(1, len(calls))


if __name__ == '__main__':
    unittest.main()
