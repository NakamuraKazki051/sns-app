-- パスワードの再設定（F-18）。メールで送ったリンクの控え
-- 確認メールと同じく、リンクそのものは残さず SHA-256 のハッシュだけを置く（DB が漏れてもリンクを作れない）
-- 1時間で切れ、1回だけ使える。アカウントを消すと一緒に消える
CREATE TABLE password_resets (
    token_hash VARCHAR(64) NOT NULL,
    user_id    VARCHAR(36) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (token_hash),
    CONSTRAINT fk_password_resets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_password_resets_user ON password_resets (user_id);
