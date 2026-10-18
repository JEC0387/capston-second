"""Contract tests use a real isolated SQLite database; no production endpoint is mocked."""
import os
os.environ['DATABASE_URL'] = ''
os.environ['DB_AUTO_CREATE'] = 'false'

import unittest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
import main
from database import Base, get_db


class OfflineStartupTest(unittest.TestCase):
    def test_db_optional_and_existing_api_survives(self):
        with TestClient(main.app) as c:
            self.assertEqual(200, c.get('/').status_code)
            self.assertEqual(200, c.get('/api/home').status_code)
            self.assertFalse(c.get('/api/health/database').json()['ready'])
            self.assertEqual(503, c.get('/api/body-measurements').status_code)
            self.assertEqual(422, c.post('/api/clothing/classify').status_code)

    def test_no_duplicate_routes(self):
        seen=set()
        for r in main.app.routes:
            for method in getattr(r,'methods',[]):
                key=(method,r.path)
                self.assertNotIn(key,seen)
                seen.add(key)


class DbApiTest(unittest.TestCase):
    def setUp(self):
        self.engine=create_engine('sqlite://',connect_args={'check_same_thread':False},poolclass=StaticPool)
        Base.metadata.create_all(self.engine)
        factory=sessionmaker(bind=self.engine,expire_on_commit=False)
        def test_db():
            with factory() as session:
                yield session
        main.app.dependency_overrides[get_db]=test_db
        self.client=TestClient(main.app)
        self.headers={'X-Device-Id':'test-device-a'}

    def tearDown(self):
        self.client.close()
        main.app.dependency_overrides.clear()
        self.engine.dispose()

    def post(self,path,payload):
        return self.client.post(path,json=payload,headers=self.headers)

    def get(self,path):
        return self.client.get(path,headers=self.headers)

    def measurement(self,**kw):
        return dict(clientId='measurement-1',bodyType='직사각형',confidence=90,
            heightCm=170,shoulderCm=42,waistCm=31,hipCm=40,legCm=78,torsoCm=50,
            shoulderToHip=1.05,waistToHip=.775,measuredAt=1791800000000,**kw)

    def test_measurement_idempotency_isolation_and_delete(self):
        self.assertEqual([],self.get('/api/body-measurements').json()['measurements'])
        payload=self.measurement()
        a=self.post('/api/body-measurements',payload)
        self.assertEqual(201,a.status_code,a.text)
        b=self.post('/api/body-measurements',payload)
        self.assertEqual(a.json()['id'],b.json()['id'])
        self.assertEqual(1,self.get('/api/body-measurements').json()['totalCount'])
        self.assertEqual(50,a.json()['torsoCm'])
        self.assertFalse(a.json()['isSample'])
        other=self.client.get('/api/body-measurements',headers={'X-Device-Id':'test-device-b'})
        self.assertEqual(0,other.json()['totalCount'])
        self.assertEqual(204,self.client.delete('/api/body-measurements',headers=self.headers).status_code)
        self.assertEqual(0,self.get('/api/body-measurements').json()['totalCount'])
        self.assertIsNone(self.get('/api/profile').json()['bodyType'])

    def test_invalid_measurements_rejected(self):
        for key,value in [('heightCm',0),('heightCm',999),('waistCm',-20),('waistCm',999),
                          ('shoulderCm','NaN'),('hipCm','Infinity'),('confidence',101),('measuredAt',0)]:
            with self.subTest(key=key,value=value):
                payload=self.measurement(); payload[key]=value
                self.assertEqual(422,self.post('/api/body-measurements',payload).status_code)
        self.assertEqual(0,self.get('/api/body-measurements').json()['totalCount'])

    def test_ratio_only_record_preserves_unavailable_cm(self):
        r=self.post('/api/body-measurements',dict(clientId='ratio-only',bodyType='삼각형',shoulderToHip=.9))
        self.assertEqual(201,r.status_code,r.text)
        self.assertIsNone(r.json()['waistCm'])

    def test_wardrobe_roundtrip_update_delete_and_isolation(self):
        item=dict(id='local-id',name='셔츠',category='상의',colorName='화이트',colorValue=0xFFFFFFFF,
                  imageUri='file:///local.png',originalUri='file:///original.jpg',isCutout=True,style='캐주얼')
        r=self.post('/api/wardrobe/sync',{'items':[item]})
        self.assertEqual(200,r.status_code,r.text)
        item['name']='수정 셔츠'
        self.post('/api/wardrobe/sync',{'items':[item]})
        row=self.get('/api/wardrobe').json()['items'][0]
        self.assertEqual('수정 셔츠',row['name']); self.assertEqual('local-id',row['clientId'])
        self.assertTrue(row['isCutout'])
        self.assertEqual(0,self.client.get('/api/wardrobe',headers={'X-Device-Id':'other'}).json()['totalCount'])
        self.post('/api/wardrobe/sync',{'items':[]})
        self.assertEqual(0,self.get('/api/wardrobe').json()['totalCount'])

    def test_wear_logs_profile_and_styles(self):
        p={'logs':[{'date':'2026-10-13','itemIds':['shirt'],'memo':'회의'}]}
        self.assertEqual(200,self.post('/api/wear-logs/sync',p).status_code)
        self.assertEqual(p['logs'],self.get('/api/wear-logs').json()['logs'])
        self.assertEqual(1,self.get('/api/profile').json()['outfitCount'])
        self.assertEqual(200,self.post('/api/profile/styles',{'styles':['캐주얼']}).status_code)
        self.assertEqual(['캐주얼'],self.get('/api/profile').json()['preferredStyles'])
        self.post('/api/profile/styles',{'styles':[]})
        self.assertEqual([],self.get('/api/profile').json()['preferredStyles'])
        r=self.post('/api/style-preference/rate',{'style':'캐주얼','like':True})
        self.assertEqual(1,r.json()['ratedCount'])
        self.assertEqual(0,self.get('/api/size-charts').json()['totalCount'])

    def test_identity_required(self):
        self.assertEqual(400,self.client.get('/api/profile').status_code)


if __name__=='__main__': unittest.main()
