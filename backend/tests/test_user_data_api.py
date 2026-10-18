"""관심 상품 · 취향 평가 기록 · 성별 · 상품 카탈로그 DB API 테스트 (격리된 SQLite, 운영 DB 접근 없음)."""
import os
os.environ['DATABASE_URL'] = ''
os.environ['DB_AUTO_CREATE'] = 'false'

import unittest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
import data
import main
from database import Base, get_db, optional_db
from seed_catalog import upsert_catalog

SHIRT = 'https://www.musinsa.com/products/6405693'
PANTS = 'https://www.musinsa.com/products/3304653'
DEMO = 'demo:detail-knit-j19'


class UserDataApiTest(unittest.TestCase):
    def setUp(self):
        self.engine = create_engine('sqlite://', connect_args={'check_same_thread': False}, poolclass=StaticPool)
        Base.metadata.create_all(self.engine)
        self.factory = sessionmaker(bind=self.engine, expire_on_commit=False)

        def test_db():
            with self.factory() as session:
                yield session
        main.app.dependency_overrides[get_db] = test_db
        main.app.dependency_overrides[optional_db] = test_db
        self.client = TestClient(main.app)
        self.headers = {'X-Device-Id': 'device-a'}

    def tearDown(self):
        self.client.close()
        main.app.dependency_overrides.clear()
        self.engine.dispose()

    def post(self, path, payload, device='device-a'):
        return self.client.post(path, json=payload, headers={'X-Device-Id': device})

    def get(self, path, device='device-a'):
        return self.client.get(path, headers={'X-Device-Id': device})

    def seed(self):
        with self.factory() as db:
            return upsert_catalog(db)

    # ── 관심 상품 ──────────────────────────────────

    def test_interest_sync_order_update_delete_and_isolation(self):
        self.seed()
        r = self.post('/api/product-interests/sync', {'productIds': [SHIRT, DEMO]})
        self.assertEqual(200, r.status_code, r.text)
        self.assertEqual({'saved': 2, 'deleted': 0, 'total': 2}, r.json())
        items = self.get('/api/product-interests').json()['items']
        self.assertEqual([SHIRT, DEMO], [i['productKey'] for i in items])
        self.assertEqual('CKS 스탠다드 슬림핏 반팔 셔츠 네이비', items[0]['name'])   # 카탈로그 정보 결합
        self.assertEqual(SHIRT, items[0]['productUrl'])
        first_added = items[0]['addedAt']

        # 순서 바꾸고 하나 빼고 하나 추가
        r = self.post('/api/product-interests/sync', {'productIds': [PANTS, SHIRT]})
        self.assertEqual({'saved': 2, 'deleted': 1, 'total': 2}, r.json())
        items = self.get('/api/product-interests').json()['items']
        self.assertEqual([PANTS, SHIRT], [i['productKey'] for i in items])
        self.assertEqual(first_added, items[1]['addedAt'])   # 처음 담은 시각은 유지

        # 다른 폰은 따로
        self.assertEqual(0, self.get('/api/product-interests', device='device-b').json()['totalCount'])
        # 프로필 개수
        profile = self.get('/api/profile').json()
        self.assertEqual(2, profile['interestCount'])
        self.assertEqual(2, profile['favoriteCount'])

        self.post('/api/product-interests/sync', {'productIds': []})
        self.assertEqual(0, self.get('/api/product-interests').json()['totalCount'])

    def test_interest_unknown_product_kept_and_duplicates_removed(self):
        r = self.post('/api/product-interests/sync', {'productIds': ['https://example.com/x', 'https://example.com/x']})
        self.assertEqual(200, r.status_code, r.text)
        items = self.get('/api/product-interests').json()['items']
        self.assertEqual(1, len(items))
        self.assertIsNone(items[0]['name'])   # 카탈로그에 없는 상품도 저장은 됨

    def test_interest_invalid_ids_rejected(self):
        self.assertEqual(422, self.post('/api/product-interests/sync', {'productIds': ['']}).status_code)
        self.assertEqual(422, self.post('/api/product-interests/sync', {'productIds': ['x' * 513]}).status_code)

    def test_popular_interests_counts_users(self):
        self.seed()
        self.post('/api/product-interests/sync', {'productIds': [SHIRT, PANTS]}, device='a')
        self.post('/api/product-interests/sync', {'productIds': [SHIRT]}, device='b')
        items = self.client.get('/api/product-interests/popular').json()['items']
        self.assertEqual(SHIRT, items[0]['productKey'])
        self.assertEqual(2, items[0]['interestCount'])
        self.assertEqual(1, items[1]['interestCount'])

    # ── 취향 평가 기록 ─────────────────────────────

    def events(self):
        return [
            {'at': 1791800000001, 'source': 'outfit', 'liked': True, 'colors': ['베이지', '베이지', '블랙'],
             'kinds': ['니트', '슬랙스'], 'pattern': '톤온톤', 'style': '미니멀', 'itemIds': ['a', 'b']},
            {'at': 1791800000002, 'source': 'card', 'liked': False, 'colors': ['레드'], 'kinds': ['셔츠'],
             'pattern': '포인트 컬러', 'style': '빈티지'},
            {'at': 1791800000003, 'source': 'worn', 'liked': True, 'colors': ['베이지'], 'kinds': ['니트'],
             'itemIds': ['a'], 'date': '2026-10-17'},
        ]

    def test_taste_sync_idempotent_and_summary(self):
        r = self.post('/api/taste-events/sync', {'events': self.events()})
        self.assertEqual({'saved': 3, 'deleted': 0, 'total': 3}, r.json())
        r = self.post('/api/taste-events/sync', {'events': self.events()})   # 같은 내용 다시 → 변화 없음
        self.assertEqual({'saved': 0, 'deleted': 0, 'total': 3}, r.json())
        s = self.get('/api/taste-events/summary').json()
        self.assertEqual(3, s['totalCount']); self.assertEqual(2, s['ratedCount']); self.assertEqual(1, s['wornCount'])
        self.assertEqual(3, s['colors']['베이지'])   # 👍 +2 (한 기록 안 중복은 한 번) + 착용 +1
        self.assertEqual(-1, s['colors']['레드'])
        self.assertEqual(3, s['kinds']['니트'])
        self.assertEqual(2, s['patterns']['톤온톤'])
        self.assertEqual(2, s['styles']['미니멀'])
        self.assertEqual('베이지', next(iter(s['colors'])))   # 높은 순

    def test_taste_worn_event_replaced(self):
        self.post('/api/taste-events/sync', {'events': self.events()})
        changed = self.events()[:2] + [{'at': 1791800000099, 'source': 'worn', 'liked': True, 'colors': ['블랙'],
                                        'kinds': ['셔츠'], 'date': '2026-10-17'}]
        r = self.post('/api/taste-events/sync', {'events': changed})
        self.assertEqual({'saved': 1, 'deleted': 1, 'total': 3}, r.json())
        self.assertEqual(0, self.get('/api/taste-events/summary', device='other').json()['totalCount'])

    def test_taste_invalid_rejected(self):
        bad = self.events()[0] | {'source': 'unknown'}
        self.assertEqual(422, self.post('/api/taste-events/sync', {'events': [bad]}).status_code)
        bad = self.events()[2] | {'date': '2026-13-01'}
        self.assertEqual(422, self.post('/api/taste-events/sync', {'events': [bad]}).status_code)

    # ── 성별 ──────────────────────────────────────

    def test_gender(self):
        self.assertIsNone(self.get('/api/profile').json()['gender'])
        r = self.post('/api/profile/gender', {'gender': 'FEMALE'})
        self.assertEqual(200, r.status_code, r.text)
        self.assertEqual('FEMALE', self.get('/api/profile').json()['gender'])
        self.assertEqual(422, self.post('/api/profile/gender', {'gender': 'OTHER'}).status_code)
        self.post('/api/profile/gender', {'gender': None})
        self.assertIsNone(self.get('/api/profile').json()['gender'])

    # ── 상품 카탈로그 ─────────────────────────────

    def test_catalog_seed_idempotent_and_products_from_db(self):
        fallback = self.client.get('/api/products').json()['products']
        self.assertEqual(len(data.SHOP_CATALOG), len(fallback))   # DB 가 비어 있으면 data.SHOP_CATALOG
        self.assertEqual((len(data.SHOP_CATALOG), 0), self.seed())
        self.assertEqual((0, len(data.SHOP_CATALOG)), self.seed())    # 다시 실행해도 중복 없음
        products = self.client.get('/api/products').json()['products']
        self.assertEqual(len(data.SHOP_CATALOG), len(products))
        self.assertEqual(SHIRT, products[0]['productKey'])
        detail = self.client.get(f"/api/products/{products[1]['id']}").json()
        self.assertEqual(61, detail['discountPercent'])                # 8,522 / 22,000
        self.assertEqual(404, self.client.get('/api/products/99999').status_code)
        self.assertEqual(1, len(self.client.get('/api/products?search=CHICKS').json()['products']))


if __name__ == '__main__':
    unittest.main()
