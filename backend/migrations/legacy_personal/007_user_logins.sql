-- 005: 구글 로그인 연결 표
-- 004 다음에 한 번 적용한다 (python apply_migrations.py 또는 Supabase SQL Editor). 기존 데이터는 지우지 않는다.
-- 여러 번 실행해도 안전하다 (IF NOT EXISTS).
BEGIN;

-- 구글 계정 고유 번호(sub) → users 한 명. 같은 구글 계정이면 어느 폰에서든 같은 옷장 · 취향을 쓴다
CREATE TABLE IF NOT EXISTS user_logins (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    name VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT now(),
    last_login_at TIMESTAMPTZ,
    CONSTRAINT uq_user_login_provider_subject UNIQUE (provider, subject)
);
CREATE INDEX IF NOT EXISTS ix_user_logins_user_id ON user_logins (user_id);

COMMIT;
