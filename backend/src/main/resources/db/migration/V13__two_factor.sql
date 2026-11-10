-- 2段階認証（F-24）。認証アプリ（TOTP）の秘密の鍵と、使い捨てのリカバリーコード
-- totp_secret は有効にしたときの鍵（Base32）。null なら2段階認証は無効
-- totp_pending_secret は設定中の鍵。認証アプリでコードを確かめられたら totp_secret に移す
-- totp_last_step は最後に使えたコードの時間枠。同じコードをもう一度使えないようにする
ALTER TABLE users ADD COLUMN totp_secret VARCHAR(64) NULL;
ALTER TABLE users ADD COLUMN totp_pending_secret VARCHAR(64) NULL;
ALTER TABLE users ADD COLUMN totp_last_step BIGINT NOT NULL DEFAULT 0;

-- リカバリーコード（スマホをなくしたとき用）。SHA-256 で持つ。使ったら used_at を入れる
CREATE TABLE recovery_codes (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    user_id   VARCHAR(36) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    used_at   DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_recovery_codes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_recovery_codes_user ON recovery_codes (user_id);
