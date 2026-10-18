-- 004: 저장한 코디(룩북) · 코디 일정 표, 옷 가격 · 브랜드 칸
-- 003 다음에 한 번 적용한다 (python apply_migrations.py 또는 Supabase SQL Editor). 기존 데이터는 지우지 않는다.
-- 여러 번 실행해도 안전하다 (IF NOT EXISTS).
BEGIN;

-- 옷 구매 정보 (선택 입력) — 착용당 비용 계산에 쓴다
ALTER TABLE clothes ADD COLUMN IF NOT EXISTS brand VARCHAR(50);
ALTER TABLE clothes ADD COLUMN IF NOT EXISTS price INTEGER;

-- 저장한 코디 (추천 카드의 🔖). client_key = 옷 id 를 정렬해 + 로 이은 값
CREATE TABLE IF NOT EXISTS saved_outfits (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    client_key VARCHAR(600) NOT NULL,
    item_ids JSON NOT NULL DEFAULT '[]',
    style VARCHAR(20),
    pattern VARCHAR(20),
    note VARCHAR(100),
    position INTEGER NOT NULL DEFAULT 0,
    saved_at TIMESTAMPTZ,
    CONSTRAINT uq_saved_outfit_user_key UNIQUE (user_id, client_key)
);
CREATE INDEX IF NOT EXISTS ix_saved_outfits_user_id ON saved_outfits (user_id);

-- 코디 일정 (날짜마다 입기로 정한 코디 하나)
CREATE TABLE IF NOT EXISTS outfit_plans (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    date VARCHAR(10) NOT NULL,
    item_ids JSON NOT NULL DEFAULT '[]',
    note VARCHAR(100),
    created_at TIMESTAMPTZ,
    CONSTRAINT uq_outfit_plan_user_date UNIQUE (user_id, date)
);
CREATE INDEX IF NOT EXISTS ix_outfit_plans_user_id ON outfit_plans (user_id);

COMMIT;
