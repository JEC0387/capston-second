"""Optional PostgreSQL persistence. Missing/unavailable DB never disables image APIs."""
import logging
import os
import time
from pathlib import Path
from typing import Optional

from dotenv import load_dotenv
from fastapi import HTTPException
from sqlalchemy import create_engine, inspect, text
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import DeclarativeBase, sessionmaker

load_dotenv(Path(__file__).with_name('.env'))
logger = logging.getLogger(__name__)


class Base(DeclarativeBase):
    pass


def normalize_db_url(url: str) -> Optional[str]:
    """
    Supabase 주소를 우리 서버가 쓰는 드라이버(psycopg2) 주소로 맞춘다. PostgreSQL 이 아니면 None.
    SQLAlchemy 2.1 부터 'postgresql://' 의 기본 드라이버가 psycopg(3) 로 바뀌어서, 드라이버를 직접 적어 준다
    (requirements.txt 에는 psycopg2-binary 만 있음).
    """
    url = url.strip().strip('"').strip("'")
    for prefix in ('postgres://', 'postgresql://'):
        if url.startswith(prefix):
            return 'postgresql+psycopg2://' + url[len(prefix):]
    if url.startswith(('postgresql+psycopg2://', 'postgresql+psycopg://')):
        return url
    return None


def _make_engine(raw: Optional[str] = None):
    raw = os.getenv('DATABASE_URL', '') if raw is None else raw
    if not raw.strip():
        return None
    url = normalize_db_url(raw)
    if url is None:
        logger.warning('DATABASE_URL must be PostgreSQL; DB endpoints are disabled')
        return None
    try:
        return create_engine(url, pool_pre_ping=True, pool_timeout=5,
                             connect_args={'sslmode': 'require', 'connect_timeout': 5})
    except ImportError:
        # DB 드라이버가 설치되지 않음 — 서버는 켜고(이미지 · AI 기능 유지) 원인을 알려 준다
        print('[DB] DB 드라이버가 없어요 → backend 폴더에서 python -m pip install -r requirements.txt 실행')
        return None
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
    # 002 · 003 · 004 마이그레이션이 빠졌는지도 알려 준다.
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
_M004 = '004_outfit_book_and_prices.sql'
_SCHEMA_CHECKS = [
    ('users.gender', 'SELECT gender FROM users LIMIT 0', _M002),
    ('products.product_key', 'SELECT product_key FROM products LIMIT 0', _M002),
    ('product_interests', 'SELECT product_key FROM product_interests LIMIT 0', _M002),
    ('taste_events', 'SELECT at_ms FROM taste_events LIMIT 0', _M002),
    ('clothing_photos', 'SELECT client_id FROM clothing_photos LIMIT 0', _M003),
    ('recommendation_logs', 'SELECT item_key FROM recommendation_logs LIMIT 0', _M003),
    ('clothes.price', 'SELECT price, brand FROM clothes LIMIT 0', _M004),
    ('saved_outfits', 'SELECT client_key FROM saved_outfits LIMIT 0', _M004),
    ('outfit_plans', 'SELECT date FROM outfit_plans LIMIT 0', _M004),
]


# ── 마이그레이션을 아직 적용하지 않은 DB 에서도 기존 기능이 멈추지 않도록 ──
# 새 표 · 칸이 있는지 확인해서, 없으면 그 기능만 건너뛴다 (옷장 · 착용 기록 같은 기존 저장은 계속 동작).
# 있는 것은 계속 기억하고, 없는 것은 30초마다 다시 확인한다 (서버를 켠 채로 apply_migrations.py 를 돌려도 반영).
_schema_cache: dict = {}
_RECHECK_SECONDS = 30


def schema_has(db, table: str, column: Optional[str] = None) -> bool:
    bind = db.get_bind()
    key = (id(bind), table, column)
    now = time.monotonic()
    hit = _schema_cache.get(key)
    if hit is not None and (hit[0] or now - hit[1] < _RECHECK_SECONDS):
        return hit[0]
    try:
        # 세션이 쓰는 연결로 확인한다 (다른 연결을 빌렸다 돌려주면서 진행 중인 저장을 되돌리지 않도록)
        insp = inspect(db.connection())
        ok = insp.has_table(table) and (column is None or any(c['name'] == column for c in insp.get_columns(table)))
    except SQLAlchemyError:
        ok = False
    _schema_cache[key] = (ok, now)
    return ok


def reset_schema_cache():
    """테스트용 — 표 구성을 바꾼 뒤 다시 확인하게 한다"""
    _schema_cache.clear()
