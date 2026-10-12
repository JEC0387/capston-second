"""
DB 연결 설정 — Supabase(PostgreSQL) 전용.

접속 주소는 코드에 적지 않고 backend/.env 파일의 DATABASE_URL 에서 읽는다.
(.env 는 .gitignore 에 들어 있어 깃허브에 올라가지 않는다. 팀원에게는 개인톡으로만 전달)

  DATABASE_URL=postgresql://postgres.xxxx:[비밀번호]@aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres

Supabase 대시보드 상단 [Connect] → "Session pooler" 주소를 그대로 복사하면 된다.
"""
import os

from dotenv import load_dotenv
from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

load_dotenv()

DATABASE_URL = os.getenv("DATABASE_URL", "").strip()

if not DATABASE_URL:
    raise RuntimeError(
        "\n\n[DB 설정 필요] backend/.env 파일에 DATABASE_URL 이 없습니다.\n"
        "1) Supabase 프로젝트 상단 Connect → Session pooler 주소 복사\n"
        "2) backend/.env.example 을 복사해 backend/.env 로 저장\n"
        "3) DATABASE_URL= 뒤에 붙여넣고 [YOUR-PASSWORD] 를 DB 비밀번호로 교체\n"
        "자세한 방법은 backend/README.md 참고\n"
    )

# 일부 서비스는 postgres:// 로 주는데 SQLAlchemy 는 postgresql:// 만 인식한다
if DATABASE_URL.startswith("postgres://"):
    DATABASE_URL = DATABASE_URL.replace("postgres://", "postgresql://", 1)

if not DATABASE_URL.startswith("postgresql"):
    raise RuntimeError(
        "\n\n[DB 설정 오류] DATABASE_URL 은 postgresql:// 로 시작하는 Supabase 주소여야 합니다.\n"
    )

engine = create_engine(
    DATABASE_URL,
    connect_args={"sslmode": "require"},   # Supabase 는 암호화 연결 필수
    pool_pre_ping=True,                    # 끊긴 연결 자동 재접속
)

SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


def get_db():
    """FastAPI Depends 용 — 요청 하나마다 DB 세션을 열고 끝나면 닫는다."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def db_label() -> str:
    """서버 시작 로그·루트(/) 응답에 표시할 접속 대상 (비밀번호는 빼고 호스트만)."""
    host = DATABASE_URL.split("@")[-1].split("/")[0]
    return f"Supabase PostgreSQL ({host})"
