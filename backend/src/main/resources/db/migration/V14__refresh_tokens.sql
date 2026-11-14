-- リフレッシュトークン（docs/auth.md）。アクセストークン（JWT、15分）が切れたら、これと引き換えに作り直す。
-- トークンそのものは置かず SHA-256 だけを持つ。使うたびに新しいものと取り替え（ローテーション）、
-- 使ったものは used_at を入れて期限まで残す。同じものがもう一度使われたら盗まれたとみなし、そのログイン（session_id）ごと無効にする
-- token_version は作ったときの users.token_version。パスワードを変えると合わなくなり、使えなくなる
CREATE TABLE refresh_tokens (
    token_hash    VARCHAR(64) NOT NULL,
    session_id    VARCHAR(36) NOT NULL,
    user_id       VARCHAR(36) NOT NULL,
    token_version INT         NOT NULL,
    expires_at    DATETIME(6) NOT NULL,
    used_at       DATETIME(6) NULL,
    PRIMARY KEY (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_tokens_session ON refresh_tokens (session_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
