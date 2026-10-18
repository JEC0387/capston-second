"""마이그레이션(002 · 003 · 004)을 아직 적용하지 않은 예전 DB 에서도 기존 기능이 멈추지 않는지 (격리된 SQLite)."""
import os
os.environ['DATABASE_URL'] = ''
os.environ['DB_AUTO_CREATE'] = 'false'

import unittest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
import main
from database import Base, get_db, optional_db, reset_schema_cache

NEW_TABLES = ('clothing_photos', 'recommendation_logs', 'saved_outfits', 'outfit_plans', 'product_interests', 'taste_events')
NEW_COLUMNS = (('clothes', 'brand'), ('clothes', 'price'), ('users', 'gender'))


class OldSchemaTest(unittest.TestCase):
    def setUp(self):
        self.engine = create_engine('sqlite://', connect_args={'check_same_thread': False}, poolclass=StaticPool)
        Base.metadata.create_all(self.engine)
        with self.engine.begin() as conn:   # 예전 DB 처럼 새 표 · 칸을 없앤다
            for t in NEW_TABLES:
                conn.execute(text(f'DROP TABLE {t}'))
            for t, c in NEW_COLUMNS:
                conn.execute(text(f'ALTER TABLE {t} DROP COLUMN {c}'))
        reset_schema_cache()
        self.factory = sessionmaker(bind=self.engine, expire_on_commit=False)

        def test_db():
            with self.factory() as session:
                yield session
        main.app.dependency_overrides[get_db] = test_db
        main.app.dependency_overrides[optional_db] = test_db
        self.client = TestClient(main.app)
        self.h = {'X-Device-Id': 'old-db'}

    def tearDown(self):
        self.client.close()
        main.app.dependency_overrides.clear()
        self.engine.dispose()
        reset_schema_cache()

    def test_existing_features_keep_working(self):
        items = [{'id': 'c1', 'name': '셔츠', 'category': '상의', 'colorName': '네이비', 'colorValue': 0xFF1F2A44,
                  'imageUri': 'file:///x.png', 'price': 39000, 'brand': 'B'}]
        r = self.client.post('/api/wardrobe/sync', json={'items': items}, headers=self.h)
        self.assertEqual(200, r.status_code, r.text)                       # 가격 · 브랜드만 건너뜀
        got = self.client.get('/api/wardrobe', headers=self.h).json()['items'][0]
        self.assertEqual(('c1', None, None, False), (got['clientId'], got['price'], got['brand'], got['hasPhoto']))
        r = self.client.post('/api/wear-logs/sync', json={'logs': [{'date': '2026-10-18', 'itemIds': ['c1'], 'memo': ''}]},
                             headers=self.h)
        self.assertEqual(200, r.status_code, r.text)
        profile = self.client.get('/api/profile', headers=self.h).json()
        self.assertEqual((1, 1, 0, None), (profile['wardrobeCount'], profile['outfitCount'], profile['interestCount'], profile['gender']))
        self.assertEqual(200, self.client.post('/api/profile/styles', json={'styles': ['미니멀']}, headers=self.h).status_code)
        self.assertEqual(503, self.client.post('/api/profile/gender', json={'gender': 'MALE'}, headers=self.h).status_code)
        # 새 기능만 503 (앱은 대기열에 두었다가 마이그레이션 뒤에 보냄)
        r = self.client.post('/api/saved-outfits/sync', json={'outfits': [{'itemIds': ['c1']}]}, headers=self.h)
        self.assertEqual(503, r.status_code)
        # 전체 통계 · 내 데이터 지우기도 동작
        s = self.client.get('/api/stats/overview').json()
        self.assertEqual((1, 1, 0, 0), (s['clothes'], s['wearLogs'], s['savedOutfits'], s['pricedClothes']))
        d = self.client.delete('/api/me', headers=self.h)
        self.assertEqual(200, d.status_code, d.text)
        self.assertEqual(1, d.json()['deleted']['clothes'])
        self.assertEqual(0, d.json()['deleted']['saved_outfits'])

    def test_health_lists_missing_migrations(self):
        import database
        old = database.engine
        database.engine = self.engine
        try:
            status = database.database_status()
        finally:
            database.engine = old
        self.assertTrue(status['ready'])
        self.assertIn('saved_outfits', status['missing'])
        self.assertIn('clothes.price', status['missing'])
        self.assertIn('004_outfit_book_and_prices.sql', status['hint'])
        self.assertIn('003_backup_and_recommendation_logs.sql', status['hint'])


if __name__ == '__main__':
    unittest.main()
