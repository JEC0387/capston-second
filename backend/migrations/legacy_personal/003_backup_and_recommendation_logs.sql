-- 003: 옷 사진 백업(다른 폰으로 복원) · 추천 기록(추천 성능 측정) 표
-- 002 다음에 한 번 적용한다 (Supabase → SQL Editor 에 붙여넣고 Run). 기존 데이터는 지우지 않는다.
-- 여러 번 실행해도 안전하다 (IF NOT EXISTS).
BEGIN;

-- 옷장 옷 사진 백업 (앱이 긴 변 720px 이하로 줄여 올린 사진 한 장, 2MB 이하)
-- 사람 · 체형 사진은 넣지 않는다 — 옷장 옷 사진만.
CREATE TABLE IF NOT EXISTS clothing_photos (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    client_id VARCHAR(64) NOT NULL,
    mime VARCHAR(20) NOT NULL,
    data BYTEA NOT NULL,
    size INTEGER NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_clothing_photo_user_client UNIQUE (user_id, client_id)
);
CREATE INDEX IF NOT EXISTS ix_clothing_photos_user_id ON clothing_photos (user_id);

-- 앱이 보여 준 추천 코디 기록 (홈 · 오늘 · 5일 예보 · 상황별)
CREATE TABLE IF NOT EXISTS recommendation_logs (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    date VARCHAR(10) NOT NULL,
    context VARCHAR(30) NOT NULL,
    scope VARCHAR(12) NOT NULL DEFAULT 'OWNED',
    rank INTEGER NOT NULL DEFAULT 0,
    item_ids JSON NOT NULL DEFAULT '[]',
    item_key VARCHAR(512) NOT NULL,
    shown_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_recommendation_log UNIQUE (user_id, date, context, item_key)
);
CREATE INDEX IF NOT EXISTS ix_recommendation_logs_user_id ON recommendation_logs (user_id);

COMMIT;
