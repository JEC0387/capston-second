"""
쇼핑 상품 카탈로그를 DB products 표에 넣는다 (앱 ShopCatalog 와 같은 9개).

    python seed_catalog.py

- product_key(= 앱 상품 id, 구매 링크)가 같은 상품이 있으면 내용을 갱신하고, 없으면 추가한다 → 여러 번 실행해도 중복 없음
- 실제 상품 정보만 넣는다 (체형 측정 같은 사용자 데이터는 만들지 않음)
- 먼저 migrations/002_user_data_tables.sql 을 적용해 product_key 칸이 있어야 한다
"""
import sys

from sqlalchemy import select

import data
from database import SessionLocal, engine
from db_models import Product


def upsert_catalog(db) -> tuple[int, int]:
    added = updated = 0
    existing = {p.product_key: p for p in db.scalars(select(Product).where(Product.product_key.is_not(None)))}
    for item in data.SHOP_CATALOG:
        p = existing.get(item["productKey"])
        if p is None:
            p = Product(product_key=item["productKey"])
            db.add(p)
            added += 1
        else:
            updated += 1
        p.name = item["name"]
        p.brand = item["brand"]
        p.mall_name = item["mallName"]
        p.price = item["price"]
        p.original_price = item["originalPrice"]
        p.rating = item["rating"]
        p.review_count = item["reviewCount"]
        p.bg_color = item["bgColor"]
        p.badge = item["badge"]
        p.category = item["category"]
        p.image_url = item["imageUrl"]
        p.product_url = item["productKey"] if item["productKey"].startswith("http") else ""
        p.description = p.description or ""
        p.colors = p.colors or []
        p.sizes = p.sizes or []
    db.commit()
    return added, updated


def main() -> None:
    if engine is None:
        sys.exit("backend/.env 의 DATABASE_URL 이 없거나 PostgreSQL 주소가 아니에요 (DB_INTEGRATION.md 참고)")
    with SessionLocal() as db:
        added, updated = upsert_catalog(db)
    print(f"상품 카탈로그: {added}개 추가, {updated}개 갱신 (총 {len(data.SHOP_CATALOG)}개)")


if __name__ == "__main__":
    main()
