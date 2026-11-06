-- 002: 남은 앱 데이터를 DB 에 저장하기 위한 표 · 칸 (관심 상품, 취향 평가 기록, 성별, 상품 카탈로그 열쇠)
-- 이미 사용 중인 DB 에 한 번 적용한다 (Supabase → SQL Editor 에 붙여넣고 Run). 기존 데이터는 지우지 않는다.
-- 여러 번 실행해도 안전하다 (IF NOT EXISTS). 빈 개발 DB 는 DB_AUTO_CREATE=true 로 만들어도 된다.
BEGIN;

-- 마이페이지 성별 설정 (FEMALE / MALE)
ALTER TABLE users ADD COLUMN IF NOT EXISTS gender VARCHAR(10);

-- 상품 카탈로그: 앱 ShopCatalog 의 상품 id(구매 링크) — 관심 상품과 연결하는 열쇠
ALTER TABLE products ADD COLUMN IF NOT EXISTS product_key VARCHAR(512);
CREATE UNIQUE INDEX IF NOT EXISTS ix_products_product_key ON products (product_key);

-- 관심 상품 (쇼핑 · 상품 상세 · 옷장 '관심 옷' 하트)
CREATE TABLE IF NOT EXISTS product_interests (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_key VARCHAR(512) NOT NULL,
    position INTEGER NOT NULL DEFAULT 0,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_interest_user_key UNIQUE (user_id, product_key)
);
CREATE INDEX IF NOT EXISTS ix_product_interests_user_id ON product_interests (user_id);
CREATE INDEX IF NOT EXISTS ix_product_interests_product_key ON product_interests (product_key);

-- 취향 평가 기록 (코디 👍👎 · 취향 카드 · 착용)
CREATE TABLE IF NOT EXISTS taste_events (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    at_ms BIGINT NOT NULL,
    source VARCHAR(10) NOT NULL,
    liked BOOLEAN NOT NULL,
    colors JSON NOT NULL DEFAULT '[]',
    kinds JSON NOT NULL DEFAULT '[]',
    pattern VARCHAR(20),
    style VARCHAR(20),
    item_ids JSON NOT NULL DEFAULT '[]',
    date VARCHAR(10),
    CONSTRAINT uq_taste_event_user_at_source UNIQUE (user_id, at_ms, source)
);
CREATE INDEX IF NOT EXISTS ix_taste_events_user_id ON taste_events (user_id);

COMMIT;
