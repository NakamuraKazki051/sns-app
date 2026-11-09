-- ログインの JWT をすぐ無効にするための2つ（docs/auth.md）
--
-- 1. JWT の世代。JWT には作ったときの世代を入れ、DB の値と違えば断る。
--    パスワードを変えると世代を進めるので、ほかの端末の JWT はすべて使えなくなる
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;

-- 2. ログアウトしたログイン。ログインごとに ID（JWT の sid）を振り、作り直しても同じ ID を使う。
--    ログアウトするとここに入り、その ID の JWT は期限前でも断る。期限を過ぎた行は消してよい
CREATE TABLE revoked_sessions (
    session_id VARCHAR(36) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (session_id)
);
CREATE INDEX idx_revoked_sessions_expires_at ON revoked_sessions (expires_at);
