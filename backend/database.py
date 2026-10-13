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
        import db_models  # register ORM tables
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


def database_status():
    if engine is None:
        return {'ready': False, 'status': 'not_configured'}
    try:
        with engine.connect() as conn:
            # Include the integration schema, not merely TCP connectivity.
            conn.execute(text('SELECT client_id FROM body_measurements LIMIT 0'))
        return {'ready': True, 'status': 'available'}
    except SQLAlchemyError:
        return {'ready': False, 'status': 'unavailable'}
