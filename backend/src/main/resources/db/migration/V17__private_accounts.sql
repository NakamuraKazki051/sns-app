-- 鍵アカウント（F-26、docs/spec.md 4.12）。is_private が TRUE の人の投稿は、本人と承認したフォロワーにだけ見える。
-- フォローするには、相手の承認が要る（follow_requests に置き、承認したら follows に移す）
ALTER TABLE users ADD COLUMN is_private BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE follow_requests (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    requester_id VARCHAR(36) NOT NULL,
    target_id    VARCHAR(36) NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_follow_requests UNIQUE (requester_id, target_id),
    CONSTRAINT fk_follow_requests_requester FOREIGN KEY (requester_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_follow_requests_target FOREIGN KEY (target_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_follow_requests_target ON follow_requests (target_id, id);

-- 通知の種類に FOLLOW_REQUEST（フォローのリクエスト）が増え、10字に入らない
ALTER TABLE notifications MODIFY type VARCHAR(20) NOT NULL;
