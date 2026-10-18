"""Optional PostgreSQL persistence. Missing/unavailable DB never disables image APIs."""
import logging
import os
from pathlib import Path

from dotenv import load_dotenv
from fastapi import HTTPException
from sqlalchemy import create_engine, text
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import DeclarativeBase, sessionmaker

load_dotenv(Path(__file__).with_name('.env'))
logger = logging.getLogger(__name__)


class Base(DeclarativeBase):
    pass


def _make_engine():
    url = os.getenv('DATABASE_URL', '').strip()
    if not url:
        return None
    if url.startswith('postgres://'):
        url = 'postgresql://' + url[len('postgres://'):]
    if not url.startswith(('postgresql://', 'postgresql+psycopg2://')):
        logger.warning('DATABASE_URL must be PostgreSQL; DB endpoints are disabled')
        return None
    try:
        return create_engine(url, pool_pre_ping=True, pool_timeout=5,
                             connect_args={'sslmode': 'require', 'connect_timeout': 5})
    except (ValueError, SQLAlchemyError):
        logger.warning('Invalid DB configuration; DB endpoints are disabled')
        return None


engine = _make_engine()
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


def initialize_database():
    """Explicit opt-in for an empty database. Never seed fabricated measurements."""
    if engine is not None and os.getenv('DB_AUTO_CREATE', 'false').lower() == 'true':
        import db_models  # noqa: F401  (register ORM tables)
        try:
            Base.metadata.create_all(engine)
        except SQLAlchemyError:
            logger.warning('DB initialization failed; local and image features remain available')


def get_db():
    if engine is None:
        raise HTTPException(503, 'Database is not configured')
    session = SessionLocal()
    try:
        yield session
    except SQLAlchemyError:
        session.rollback()
        # Do not expose credentials, SQL, or records in error responses.
        raise HTTPException(503, 'Database is temporarily unavailable') from None
    finally:
        session.close()


def optional_db():
    """DB 가 있으면 세션, 없거나 연결이 안 되면 None — DB 가 없어도 동작해야 하는 API(상품 목록 등)용."""
    if engine is None:
        yield None
        return
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()


def database_status():
    if engine is None:
        return {'ready': False, 'status': 'not_configured'}
    try:
        with engine.connect() as conn:
            # Include the integration schema, not merely TCP connectivity.
            conn.execute(text('SELECT client_id FROM body_measurements LIMIT 0'))
    except SQLAlchemyError:
        return {'ready': False, 'status': 'unavailable'}
    # 002 · 003 마이그레이션이 빠졌는지도 알려 준다.
    # 빠져 있어도 ready 는 True — 기존 기능은 그대로 동작하고, 새 항목만 저장되지 않는다.
    missing, hints = [], []
    for label, sql, migration in _SCHEMA_CHECKS:
        try:
            with engine.connect() as conn:
                conn.execute(text(sql))
        except SQLAlchemyError:
            missing.append(label)
            if migration not in hints:
                hints.append(migration)
    result = {'ready': True, 'status': 'available'}
    if missing:
        result['missing'] = missing
        result['hint'] = 'Apply ' + ', '.join(f'backend/migrations/{m}' for m in hints)
    return result


_M002 = '002_user_data_tables.sql'
_M003 = '003_backup_and_recommendation_logs.sql'
_SCHEMA_CHECKS = [
    ('users.gender', 'SELECT gender FROM users LIMIT 0', _M002),
    ('products.product_key', 'SELECT product_key FROM products LIMIT 0', _M002),
    ('product_interests', 'SELECT product_key FROM product_interests LIMIT 0', _M002),
    ('taste_events', 'SELECT at_ms FROM taste_events LIMIT 0', _M002),
    ('clothing_photos', 'SELECT client_id FROM clothing_photos LIMIT 0', _M003),
    ('recommendation_logs', 'SELECT item_key FROM recommendation_logs LIMIT 0', _M003),
]
