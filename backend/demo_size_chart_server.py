"""Explicit development-only server with sample size charts in an isolated in-memory SQLite DB.

Run this script directly for an emulator demo. It never connects to or seeds Supabase/PostgreSQL.
"""
import os

from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool


DEMO_BRAND = '개발용 샘플 (공식 아님)'
DEMO_SHOULDERS = {'S': 38.0, 'M': 41.0, 'L': 44.0, 'XL': 47.0}


def seed_demo_size_charts(session: Session) -> None:
    """Seed only the explicitly created demo database; never call this from normal startup."""
    from db_models import SizeChart

    if session.scalar(select(SizeChart.id).where(SizeChart.brand == DEMO_BRAND).limit(1)) is not None:
        return
    session.add_all([
        SizeChart(brand=DEMO_BRAND, category='상의', size_label=label,
                  shoulder_cm=shoulder, measure_type='단면', source_url='')
        for label, shoulder in DEMO_SHOULDERS.items()
    ])
    session.commit()


def create_demo_app():
    """Return the existing FastAPI app backed by real, temporary SQLite ORM tables."""
    # Set these before importing database/main so a local .env cannot select production.
    os.environ['DATABASE_URL'] = ''
    os.environ['DB_AUTO_CREATE'] = 'false'
    import database
    import main
    import db_models  # noqa: F401 - registers all ORM tables before create_all

    engine = create_engine('sqlite://', connect_args={'check_same_thread': False},
                           poolclass=StaticPool)
    database.Base.metadata.create_all(engine)
    factory = sessionmaker(bind=engine, expire_on_commit=False)
    with factory() as session:
        seed_demo_size_charts(session)
    # The normal endpoints and health check share this real, isolated demo DB.
    database.engine = engine
    database.SessionLocal.configure(bind=engine)
    return main.app, engine


def close_demo_app(engine) -> None:
    """Release demo DB state after an in-process test."""
    import database

    database.engine = None
    database.SessionLocal.configure(bind=None)
    engine.dispose()


if __name__ == '__main__':
    import argparse
    import uvicorn

    parser = argparse.ArgumentParser(description='Development-only size-chart demo server')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=8000)
    args = parser.parse_args()
    app, _engine = create_demo_app()
    uvicorn.run(app, host=args.host, port=args.port)
