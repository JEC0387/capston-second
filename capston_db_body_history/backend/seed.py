"""
DB 초기화 — 서버 시작 시 main.py 가 init_db() 를 한 번 호출한다.

1) db_models.py 의 테이블이 Supabase 에 없으면 만든다.
   이미 있으면 그대로 두고, 예전 버전 테이블이면 새로 필요한 칸만 덧붙인다.
2) 데모 사용자가 없으면 만든다.
3) 옷장·상품이 비어 있을 때만 data.py 의 초기 데이터를 넣는다.
   → 두 번 실행해도 중복으로 들어가지 않는다.

직접 실행해서 테이블만 만들어 볼 수도 있다:  python seed.py
"""
from datetime import datetime, timezone

from sqlalchemy import func, inspect, select, text

import data
from database import Base, SessionLocal, engine
from db_models import Clothing, Product, User

DEMO_EMAIL = data.PROFILE["email"]


def _upgrade_old_tables() -> None:
    """
    예전 버전 코드로 이미 만든 테이블이 있으면, 새 버전에 필요한 칸을 덧붙인다.
    (create_all 은 없는 테이블만 만들고, 있는 테이블에 칸을 추가해 주지는 않기 때문)
    - 새로 생긴 칸: 빈 값 허용으로 추가
    - 새 버전에서 안 쓰는 옛날 칸: 빈 값 허용으로 바꿔서 저장이 막히지 않게 함 (데이터는 지우지 않음)
    """
    insp = inspect(engine)
    existing_tables = set(insp.get_table_names())
    with engine.begin() as conn:
        for table in Base.metadata.sorted_tables:
            if table.name not in existing_tables:
                continue
            db_cols = {c["name"]: c for c in insp.get_columns(table.name)}
            for col in table.columns:
                if col.name not in db_cols:
                    col_type = col.type.compile(dialect=engine.dialect)
                    conn.execute(text(f'ALTER TABLE "{table.name}" ADD COLUMN IF NOT EXISTS "{col.name}" {col_type}'))
                    print(f"[DB] {table.name}.{col.name} 칸 추가 (예전 버전 테이블 업그레이드)")
                elif col.nullable and not db_cols[col.name]["nullable"]:
                    conn.execute(text(f'ALTER TABLE "{table.name}" ALTER COLUMN "{col.name}" DROP NOT NULL'))
            for name, info in db_cols.items():
                if name not in table.columns and not info["nullable"]:
                    conn.execute(text(f'ALTER TABLE "{table.name}" ALTER COLUMN "{name}" DROP NOT NULL'))


def init_db() -> None:
    import db_models  # noqa: F401  (테이블 정의를 Base 에 등록)

    Base.metadata.create_all(bind=engine)
    _upgrade_old_tables()

    with SessionLocal() as db:
        user = db.scalar(select(User).where(User.email == DEMO_EMAIL))
        if user is None:
            user = User(
                email=DEMO_EMAIL,
                nickname=data.PROFILE["nickname"],
                preferred_styles=list(data.PROFILE["preferredStyles"]),
            )
            db.add(user)
            db.flush()

        # id 를 직접 넣지 않는다 → PostgreSQL 자동 번호(sequence)가 꼬이지 않음
        clothes_count = db.scalar(
            select(func.count()).select_from(Clothing).where(Clothing.user_id == user.id)
        )
        if clothes_count == 0:
            now = datetime.now(timezone.utc)
            for s in data.WARDROBE_SAMPLES:
                db.add(Clothing(
                    user_id=user.id,
                    client_id=s["clientId"],
                    name=s["name"],
                    category=s["category"],
                    sub_category=s["subCategory"],
                    detail_type=s["detailType"],
                    season=s["season"],
                    color=s["color"],
                    color_name=s["colorName"],
                    image_url=s["imageUrl"],
                    original_uri=s["originalUri"],
                    cutout_uri=s["cutoutUri"],
                    garment_uri=s["garmentUri"],
                    is_cutout=s["isCutout"],
                    added_at=now,
                ))

        if db.scalar(select(func.count()).select_from(Product)) == 0:
            for p in data.PRODUCTS:   # 순서대로 넣어서 id 1~8 이 앱 화면 순서와 같아진다
                db.add(Product(
                    mall_name=p["mallName"],
                    brand=p["brand"],
                    name=p["name"],
                    price=p["price"],
                    original_price=p["originalPrice"],
                    rating=p["rating"],
                    review_count=p["reviewCount"],
                    bg_color=p["bgColor"],
                    badge=p["badge"],
                    category=p["category"],
                    image_url=p["imageUrl"],
                    product_url=p["productUrl"],
                    description=p["description"],
                    colors=p["colors"],
                    sizes=p["sizes"],
                ))

        db.commit()


if __name__ == "__main__":
    from database import db_label

    init_db()
    print(f"[DB] 테이블 생성·초기 데이터 확인 완료 → {db_label()}")
