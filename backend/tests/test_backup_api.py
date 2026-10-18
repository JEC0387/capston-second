"""옷 사진 백업 · 복원용 조회 · 추천 기록 / 성능 · 내 데이터 지우기 API 테스트 (격리된 SQLite, 운영 DB 접근 없음)."""
import os
os.environ['DATABASE_URL'] = ''
os.environ['DB_AUTO_CREATE'] = 'false'

import base64
import unittest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, func, select
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
import main
from database import Base, get_db, optional_db
from db_models import ClothingPhoto, RecommendationLog, User

PNG = b'\x89PNG\r\n\x1a\n' + b'\x00' * 32
JPEG = b'\xff\xd8\xff\xe0' + b'\x00' * 32


def b64(raw: bytes) -> str:
    return base64.b64encode(raw).decode()


def item(cid, name='셔츠', image='file:///data/user/0/x/files/wardrobe/a.png'):
    return {'id': cid, 'name': name, 'category': '상의', 'colorName': '네이비', 'colorValue': 0xFF1F2A44,
            'imageUri': image, 'addedAt': 1791800000000, 'isCutout': True,
            'cutoutUri': image, 'originalUri': 'file:///orig.jpg'}


class BackupApiTest(unittest.TestCase):
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

    # ── 옷 사진 백업 ──────────────────────────────

    def test_photo_upload_download_and_restore_fields(self):
        self.assertEqual(404, self.post('/api/wardrobe/photos', {'clientId': 'c1', 'mime': 'image/png',
                                                                'data': b64(PNG)}).status_code)  # 옷이 아직 없음
        self.post('/api/wardrobe/sync', {'items': [item('c1'), item('sample_01', image='android.resource://x')]})
        r = self.post('/api/wardrobe/photos', {'clientId': 'c1', 'mime': 'image/png', 'data': b64(PNG)})
        self.assertEqual(200, r.status_code, r.text)
        self.assertEqual(len(PNG), r.json()['size'])
        got = self.get('/api/wardrobe/photos/c1')
        self.assertEqual(200, got.status_code)
        self.assertEqual(PNG, got.content)
        self.assertEqual('image/png', got.headers['content-type'])
        # 다시 보내면 바꿔 쓴다
        self.post('/api/wardrobe/photos', {'clientId': 'c1', 'mime': 'image/jpeg', 'data': b64(JPEG)})
        self.assertEqual(JPEG, self.get('/api/wardrobe/photos/c1').content)
        self.assertEqual(1, self.count(ClothingPhoto))
        # 옷장 조회에 복원용 정보
        items = {i['clientId']: i for i in self.get('/api/wardrobe').json()['items']}
        self.assertTrue(items['c1']['hasPhoto'])
        self.assertFalse(items['sample_01']['hasPhoto'])
        self.assertEqual('file:///orig.jpg', items['c1']['originalUri'])
        self.assertEqual('#1F2A44', items['c1']['color'])
        # 다른 폰은 못 받음
        self.assertEqual(404, self.get('/api/wardrobe/photos/c1', device='device-b').status_code)

    def test_photo_validation(self):
        self.post('/api/wardrobe/sync', {'items': [item('c1')]})
        bad_mime = self.post('/api/wardrobe/photos', {'clientId': 'c1', 'mime': 'image/png', 'data': b64(JPEG)})
        self.assertEqual(422, bad_mime.status_code)
        not_b64 = self.post('/api/wardrobe/photos', {'clientId': 'c1', 'mime': 'image/png', 'data': '###not-base64###'})
        self.assertEqual(422, not_b64.status_code)
        gif = self.post('/api/wardrobe/photos', {'clientId': 'c1', 'mime': 'image/gif', 'data': b64(PNG)})
        self.assertEqual(422, gif.status_code)
        big = self.post('/api/wardrobe/photos', {'clientId': 'c1', 'mime': 'image/png',
                                                 'data': b64(PNG + b'\x00' * (2 * 1024 * 1024))})
        self.assertIn(big.status_code, (413, 422))
        self.assertEqual(0, self.count(ClothingPhoto))

    def test_wardrobe_sync_removes_photo_of_deleted_clothes(self):
        self.post('/api/wardrobe/sync', {'items': [item('c1'), item('c2')]})
        self.post('/api/wardrobe/photos', {'clientId': 'c1', 'mime': 'image/png', 'data': b64(PNG)})
        self.post('/api/wardrobe/photos', {'clientId': 'c2', 'mime': 'image/png', 'data': b64(PNG)})
        self.post('/api/wardrobe/sync', {'items': [item('c2')]})
        self.assertEqual(404, self.get('/api/wardrobe/photos/c1').status_code)
        self.assertEqual(200, self.get('/api/wardrobe/photos/c2').status_code)

    # ── 복원용 취향 기록 목록 ─────────────────────

    def test_taste_events_list_roundtrip(self):
        events = [
            {'at': 1791800000002, 'source': 'card', 'liked': False, 'colors': ['레드'], 'kinds': ['셔츠']},
            {'at': 1791800000001, 'source': 'outfit', 'liked': True, 'colors': ['베이지'], 'kinds': ['니트'],
             'pattern': '톤온톤', 'style': '미니멀', 'itemIds': ['a', 'b']},
        ]
        self.post('/api/taste-events/sync', {'events': events})
        body = self.get('/api/taste-events').json()
        self.assertEqual(2, body['totalCount'])
        self.assertEqual([1791800000001, 1791800000002], [e['at'] for e in body['events']])   # 오래된 순
        self.assertEqual(['a', 'b'], body['events'][0]['itemIds'])
        # 받은 그대로 다시 보내도 변화 없음 (복원 후 폰이 다시 올리는 경우)
        r = self.post('/api/taste-events/sync', {'events': body['events']})
        self.assertEqual({'saved': 0, 'deleted': 0, 'total': 2}, r.json())

    # ── 추천 기록 · 추천 성능 ──────────────────────

    def rec(self, date, context, ids, rank=0):
        return {'date': date, 'context': context, 'scope': 'OWNED', 'rank': rank, 'itemIds': ids,
                'shownAt': 1791800000000}

    def test_recommendation_sync_is_insert_only_and_idempotent(self):
        logs = [self.rec('2026-10-18', 'home', ['b', 'a']), self.rec('2026-10-18', 'home', ['a', 'b']),
                self.rec('2026-10-18', 'situation:INTERVIEW', ['a', 'b'])]
        r = self.post('/api/recommendations/sync', {'logs': logs})
        self.assertEqual({'saved': 2, 'deleted': 0, 'total': 2}, r.json())   # 순서만 다른 같은 코디는 한 번
        r = self.post('/api/recommendations/sync', {'logs': []})            # 폰에서 지워져도 DB 는 유지
        self.assertEqual({'saved': 0, 'deleted': 0, 'total': 2}, r.json())
        self.assertEqual(422, self.post('/api/recommendations/sync',
                                        {'logs': [self.rec('2026-13-01', 'home', ['a'])]}).status_code)
        self.assertEqual(422, self.post('/api/recommendations/sync',
                                        {'logs': [self.rec('2026-10-18', 'home', [])]}).status_code)
        self.assertEqual(422, self.post('/api/recommendations/sync',
                                        {'logs': [self.rec('2026-10-18', 'bad context!', ['a'])]}).status_code)

    def test_recommendation_stats(self):
        self.post('/api/recommendations/sync', {'logs': [
            self.rec('2026-10-17', 'home', ['a', 'b', 'c']),                  # 👍 + 그대로 입음(다음 날)
            self.rec('2026-10-17', 'today', ['d', 'e']),                      # 👎
            self.rec('2026-10-17', 'situation:INTERVIEW', ['a', 'x', 'y']),   # 일부만 입음 (1/3 → 부분 아님)
            self.rec('2026-10-17', 'situation:DATE', ['a', 'b', 'z']),        # 2/3 입음 → 부분
        ]})
        self.post('/api/taste-events/sync', {'events': [
            {'at': 1, 'source': 'outfit', 'liked': False, 'itemIds': ['c', 'b', 'a']},
            {'at': 2, 'source': 'outfit', 'liked': True, 'itemIds': ['a', 'b', 'c']},   # 마지막 평가가 👍
            {'at': 3, 'source': 'outfit', 'liked': False, 'itemIds': ['d', 'e']},
        ]})
        self.post('/api/wear-logs/sync', {'logs': [{'date': '2026-10-18', 'itemIds': ['a', 'b', 'c'], 'memo': ''},
                                                   {'date': '2026-10-25', 'itemIds': ['d', 'e'], 'memo': ''}]})
        s = self.get('/api/recommendations/stats').json()
        t = s['total']
        self.assertEqual((4, 1, 1, 1, 2), (t['shown'], t['liked'], t['disliked'], t['worn'], t['wornPartial']))
        self.assertEqual(0.25, t['likeRate'])
        self.assertEqual(0.5, t['ratedLikeRate'])
        self.assertEqual(0.5, t['wornPartialRate'])
        self.assertEqual({'home', 'today', 'situation'}, set(s['byContext']))   # 상황별은 하나로 묶음
        self.assertEqual(2, s['byContext']['situation']['shown'])
        self.assertEqual(('2026-10-17', '2026-10-17', 1), (s['since'], s['until'], s['users']))
        # 전체 통계는 기기 번호 없이, 다른 사람 기록도 합친다
        self.post('/api/recommendations/sync', {'logs': [self.rec('2026-10-18', 'home', ['q'])]}, device='device-b')
        all_stats = self.client.get('/api/recommendations/stats/all').json()
        self.assertEqual(5, all_stats['total']['shown'])
        self.assertEqual(2, all_stats['users'])
        self.assertEqual(0, self.get('/api/recommendations/stats', device='device-c').json()['total']['shown'])

    # ── 내 데이터 모두 지우기 ─────────────────────

    def test_delete_my_data_only_mine(self):
        for device in ('device-a', 'device-b'):
            self.post('/api/wardrobe/sync', {'items': [item('c1')]}, device=device)
            self.post('/api/wardrobe/photos', {'clientId': 'c1', 'mime': 'image/png', 'data': b64(PNG)}, device=device)
            self.post('/api/wear-logs/sync', {'logs': [{'date': '2026-10-18', 'itemIds': ['c1'], 'memo': ''}]},
                      device=device)
            self.post('/api/recommendations/sync', {'logs': [self.rec('2026-10-18', 'home', ['c1'])]}, device=device)
            self.post('/api/product-interests/sync', {'productIds': ['https://example.com/p']}, device=device)
        r = self.client.delete('/api/me', headers={'X-Device-Id': 'device-a'})
        self.assertEqual(200, r.status_code, r.text)
        d = r.json()['deleted']
        self.assertEqual(1, d['clothes'])
        self.assertEqual(1, d['clothing_photos'])
        self.assertEqual(1, d['recommendation_logs'])
        self.assertEqual(1, self.count(ClothingPhoto))
        self.assertEqual(1, self.count(RecommendationLog))
        with self.factory() as db:
            self.assertEqual(['device-b'], list(db.scalars(select(User.device_id))))
        self.assertEqual(0, self.get('/api/wardrobe').json()['totalCount'])        # 새 빈 사용자
        self.assertEqual(1, self.get('/api/wardrobe', device='device-b').json()['totalCount'])


if __name__ == '__main__':
    unittest.main()
