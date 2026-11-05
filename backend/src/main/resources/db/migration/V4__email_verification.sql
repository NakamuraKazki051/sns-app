-- メール認証。確認が済むまで null で、ログインできない
-- この機能より前からのアカウントは、確認済みとして扱う

ALTER TABLE users ADD COLUMN email_verified_at DATETIME(6) NULL;
UPDATE users SET email_verified_at = COALESCE(created_at, CURRENT_TIMESTAMP(6));

-- 確認リンクの控え。リンクそのものは残さず、SHA-256 のハッシュだけを置く（DB が漏れてもリンクを作れない）
-- email は確認できたら使うアドレス。登録のときは users.email と同じで、変更のときは新しいアドレス
CREATE TABLE email_verifications (
    token_hash VARCHAR(64)  NOT NULL,
    user_id    VARCHAR(36)  NOT NULL,
    email      VARCHAR(255) NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (token_hash),
    CONSTRAINT fk_email_verifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_email_verifications_user ON email_verifications (user_id);
