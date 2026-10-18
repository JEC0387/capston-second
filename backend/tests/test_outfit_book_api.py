"""저장한 코디 · 코디 일정 · 옷 가격/브랜드 · 전체 통계 API 테스트 (격리된 SQLite, 운영 DB 접근 없음)."""
import os
os.environ['DATABASE_URL'] = ''
os.environ['DB_AUTO_CREATE'] = 'false'

import unittest
from pathlib import Path
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, func, select
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
import main
from database import Base, get_db, optional_db
from db_models import OutfitPlan, SavedOutfit


def item(cid, price=None, brand=None, color='네이비', category='상의'):
    return {'id': cid, 'name': cid, 'category': category, 'colorName': color, 'colorValue': 0xFF1F2A44,
            'imageUri': 'android.resource://x', 'price': price, 'brand': brand}


class OutfitBookApiTest(unittest.TestCase):
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

    def tearDown(self):
        self.client.close()
        main.app.dependency_overrides.clear()
        self.engine.dispose()

    def post(self, path, payload, device='device-a'):
        return self.client.post(path, json=payload, headers={'X-Device-Id': device})

    def get(self, path, device='device-a'):
        return self.client.get(path, headers={'X-Device-Id': device})

    def count(self, model):
        with self.factory() as db:
            return db.scalar(select(func.count()).select_from(model))

    # ── 옷 가격 · 브랜드 ─────────────────────────

    def test_price_and_brand_saved_and_invalid_price_dropped(self):
        r = self.post('/api/wardrobe/sync', {'items': [
            item('a', 39000, '  무신사 스탠다드  '), item('b', 0), item('c', 200_000_000, 'x' * 80), item('d')]})
        self.assertEqual(200, r.status_code, r.text)
        got = {i['clientId']: i for i in self.get('/api/wardrobe').json()['items']}
        self.assertEqual((39000, '무신사 스탠다드'), (got['a']['price'], got['a']['brand']))
        self.assertIsNone(got['b']['price'])               # 0원은 비움
        self.assertIsNone(got['c']['price'])               # 1억 원 넘으면 비움 (동기화는 막지 않음)
        self.assertEqual(50, len(got['c']['brand']))
        self.assertIsNone(got['d']['brand'])

    # ── 저장한 코디 ──────────────────────────────

    def test_saved_outfits_sync_order_dedupe_delete(self):
        outfits = [
            {'id': 'a+b', 'itemIds': ['b', 'a'], 'style': '미니멀', 'pattern': '톤온톤', 'note': '회식', 'savedAt': 1791800000000},
            {'itemIds': ['a', 'b'], 'style': '캐주얼'},                         # 같은 조합 → 무시
            {'id': 'c+d', 'itemIds': ['c', 'd'], 'style': '캐주얼', 'savedAt': 1791700000000},
        ]
        r = self.post('/api/saved-outfits/sync', {'outfits': outfits})
        self.assertEqual({'saved': 2, 'deleted': 0, 'total': 2}, r.json())
        body = self.get('/api/saved-outfits').json()
        self.assertEqual(['a+b', 'c+d'], [o['id'] for o in body['outfits']])      # 폰 목록 순서
        self.assertEqual(['a', 'b'], body['outfits'][0]['itemIds'])
        self.assertEqual(('회식', '톤온톤', 1791800000000),
                         (body['outfits'][0]['note'], body['outfits'][0]['pattern'], body['outfits'][0]['savedAt']))
        # 하나 지우고 순서 바꿈
        r = self.post('/api/saved-outfits/sync', {'outfits': [outfits[2]]})
        self.assertEqual({'saved': 1, 'deleted': 1, 'total': 1}, r.json())
        self.assertEqual(0, self.get('/api/saved-outfits', device='device-b').json()['totalCount'])
        self.assertEqual(422, self.post('/api/saved-outfits/sync', {'outfits': [{'itemIds': []}]}).status_code)
        self.assertEqual(422, self.post('/api/saved-outfits/sync', {'outfits': [{'itemIds': ['x' * 65]}]}).status_code)

    # ── 코디 일정 ────────────────────────────────

    def test_outfit_plans_sync(self):
        plans = [{'date': '2026-10-20', 'itemIds': ['a', 'b'], 'note': '발표날', 'createdAt': 1791800000000},
                 {'date': '2026-10-19', 'itemIds': ['c']}]
        r = self.post('/api/outfit-plans/sync', {'plans': plans})
        self.assertEqual({'saved': 2, 'deleted': 0, 'total': 2}, r.json())
        body = self.get('/api/outfit-plans').json()
        self.assertEqual(['2026-10-19', '2026-10-20'], [p['date'] for p in body['plans']])   # 날짜 순
        self.assertEqual('발표날', body['plans'][1]['note'])
        # 같은 날 바꾸기 + 다른 날 지우기
        r = self.post('/api/outfit-plans/sync', {'plans': [{'date': '2026-10-20', 'itemIds': ['d']}]})
        self.assertEqual({'saved': 1, 'deleted': 1, 'total': 1}, r.json())
        self.assertEqual(['d'], self.get('/api/outfit-plans').json()['plans'][0]['itemIds'])
        dup = {'plans': [{'date': '2026-10-20', 'itemIds': ['a']}, {'date': '2026-10-20', 'itemIds': ['b']}]}
        self.assertEqual(422, self.post('/api/outfit-plans/sync', dup).status_code)
        self.assertEqual(422, self.post('/api/outfit-plans/sync', {'plans': [{'date': '2026-02-30', 'itemIds': ['a']}]}).status_code)

    def test_delete_me_removes_outfit_book(self):
        self.post('/api/saved-outfits/sync', {'outfits': [{'itemIds': ['a']}]})
        self.post('/api/outfit-plans/sync', {'plans': [{'date': '2026-10-20', 'itemIds': ['a']}]})
        self.post('/api/saved-outfits/sync', {'outfits': [{'itemIds': ['a']}]}, device='device-b')
        d = self.client.delete('/api/me', headers={'X-Device-Id': 'device-a'}).json()['deleted']
        self.assertEqual((1, 1), (d['saved_outfits'], d['outfit_plans']))
        self.assertEqual(1, self.count(SavedOutfit))
        self.assertEqual(0, self.count(OutfitPlan))

    # ── 전체 통계 ────────────────────────────────

    def test_overview_stats(self):
        self.post('/api/wardrobe/sync', {'items': [item('a', 30000), item('b', 50000, color='블랙'),
                                                   item('c', color='블랙', category='하의')]})
        self.post('/api/wardrobe/sync', {'items': [item('a', color='블랙', category='하의')]}, device='device-b')
        self.post('/api/profile/styles', {'styles': ['미니멀', '캐주얼']})
        self.post('/api/profile/styles', {'styles': ['미니멀']}, device='device-b')
        self.post('/api/outfit-plans/sync', {'plans': [{'date': '2026-01-02', 'itemIds': ['a', 'b']},
                                                       {'date': '2026-01-03', 'itemIds': ['a']},
                                                       {'date': '2026-01-04', 'itemIds': ['c']}]})
        self.post('/api/wear-logs/sync', {'logs': [{'date': '2026-01-02', 'itemIds': ['a', 'b', 'c'], 'memo': ''},
                                                   {'date': '2026-01-03', 'itemIds': ['b'], 'memo': ''}]})
        s = self.client.get('/api/stats/overview').json()
        self.assertEqual((2, 4, 3), (s['users'], s['clothes'], s['outfitPlans']))
        self.assertEqual({'name': '블랙', 'count': 3}, s['topColors'][0])
        self.assertEqual({'name': '상의', 'count': 2}, s['topCategories'][0])     # 하의도 2 → 이름 순
        self.assertEqual({'name': '미니멀', 'count': 2}, s['topPreferredStyles'][0])
        self.assertEqual((2, 40000), (s['pricedClothes'], s['averagePrice']))
        self.assertEqual((2, 1), (s['planDays'], s['planFollowed']))   # 기록 있는 날 2 · 그중 일정대로 1

    # ── 마이그레이션 파일 ────────────────────────

    def test_migration_004_mentions_every_new_column(self):
        sql = Path(__file__).resolve().parents[1].joinpath('migrations', '004_outfit_book_and_prices.sql').read_text(encoding='utf-8')
        for table in (SavedOutfit.__table__, OutfitPlan.__table__):
            self.assertIn(f'CREATE TABLE IF NOT EXISTS {table.name}', sql)
            for col in table.columns:
                self.assertIn(col.name, sql, f'{table.name}.{col.name}')
        self.assertIn('ADD COLUMN IF NOT EXISTS brand', sql)
        self.assertIn('ADD COLUMN IF NOT EXISTS price', sql)


if __name__ == '__main__':
    unittest.main()
