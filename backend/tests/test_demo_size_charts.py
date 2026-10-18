"""Real SQLite ORM and the existing HTTP router, with no production DB connection."""
import os
os.environ['DATABASE_URL'] = ''
os.environ['DB_AUTO_CREATE'] = 'false'

import unittest

from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from demo_size_chart_server import (
    DEMO_BRAND, DEMO_SHOULDERS, close_demo_app, create_demo_app, seed_demo_size_charts,
)
from db_models import SizeChart


class DemoSizeChartTest(unittest.TestCase):
    def setUp(self):
        self.app, self.engine = create_demo_app()
        self.client = TestClient(self.app)

    def tearDown(self):
        self.client.close()
        close_demo_app(self.engine)

    def test_sample_rows_are_returned_by_existing_api(self):
        self.assertTrue(self.client.get('/api/health/database').json()['ready'])
        response = self.client.get('/api/size-charts')
        self.assertEqual(200, response.status_code, response.text)
        payload = response.json()
        self.assertEqual(4, payload['totalCount'])
        self.assertEqual(DEMO_SHOULDERS,
                         {row['sizeLabel']: row['shoulderCm'] for row in payload['rows']})
        self.assertTrue(all(row['brand'] == DEMO_BRAND and row['category'] == '상의'
                            and row['measureType'] == '단면' for row in payload['rows']))

    def test_filter_and_empty_chart_response(self):
        self.assertEqual(4, self.client.get('/api/size-charts',
                         params={'brand': DEMO_BRAND, 'category': '상의'}).json()['totalCount'])
        empty = self.client.get('/api/size-charts', params={'category': '하의'}).json()
        self.assertEqual({'totalCount': 0, 'rows': []}, empty)

    def test_seed_is_idempotent_and_contains_no_body_records(self):
        with Session(self.engine) as session:
            seed_demo_size_charts(session)
            self.assertEqual(4, session.query(SizeChart).count())
        response = self.client.get('/api/body-measurements',
                                   headers={'X-Device-Id': 'demo-device'})
        self.assertEqual(0, response.json()['totalCount'])


if __name__ == '__main__':
    unittest.main()
