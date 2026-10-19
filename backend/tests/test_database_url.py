"""Supabase 주소 → psycopg2 드라이버 주소 (SQLAlchemy 2.1 의 기본 드라이버 변경 대비). DB 에 접속하지 않는다."""
import os
os.environ['DATABASE_URL'] = ''

import unittest
import database

URL = 'postgres.ref:pw@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres'


class DatabaseUrlTest(unittest.TestCase):
    def test_normalize(self):
        for given in (f'postgresql://{URL}', f'postgres://{URL}', f' "postgresql://{URL}" ', f'postgresql+psycopg2://{URL}'):
            self.assertEqual(f'postgresql+psycopg2://{URL}', database.normalize_db_url(given), given)
        self.assertEqual(f'postgresql+psycopg://{URL}', database.normalize_db_url(f'postgresql+psycopg://{URL}'))
        self.assertIsNone(database.normalize_db_url('mysql://x'))
        self.assertIsNone(database.normalize_db_url('sqlite:///a.db'))

    def test_engine_uses_installed_psycopg2_driver(self):
        # create_engine 은 접속하지 않으므로 가짜 주소로 드라이버만 확인
        engine = database._make_engine(f'postgresql://{URL}')
        self.assertIsNotNone(engine)
        self.assertEqual('psycopg2', engine.dialect.driver)
        engine.dispose()
        self.assertIsNone(database._make_engine(''))
        self.assertIsNone(database._make_engine('mysql://x'))


if __name__ == '__main__':
    unittest.main()
