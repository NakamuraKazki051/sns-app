-- ログイン中の端末の一覧（docs/auth.md「ログイン中の端末」）。1回のログイン（JWT の sid）につき1行。
-- 設定画面に「どの端末で・いつログインし・最後にいつ使ったか」を出し、選んだ端末だけログアウトできるようにする。
-- user_agent はブラウザが送ってくる User-Agent（255字まで）。画面には「Chrome（Windows）」のようにまとめて出す。IP アドレスは置かない
-- token_version は作ったときの users.token_version。パスワードを変えると合わなくなり、一覧から外れる
-- expires_at はリフレッシュトークンの期限（使うたびに延びる）。過ぎたらもうログインしていない
CREATE TABLE user_sessions (
    id            VARCHAR(36)  NOT NULL,
    user_id       VARCHAR(36)  NOT NULL,
    token_version INT          NOT NULL,
    user_agent    VARCHAR(255) NULL,
    created_at    DATETIME(6)  NOT NULL,
    last_used_at  DATETIME(6)  NOT NULL,
    expires_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_user_sessions_user ON user_sessions (user_id, last_used_at);
CREATE INDEX idx_user_sessions_expires_at ON user_sessions (expires_at);
