-- DM（F-23）。2人の間のメッセージ。会話は「相手」で決まるので、会話のテーブルは作らない
-- body は500字まで（見た目の1文字で数える。絵文字を考えて列は長め）。read_at は受け取った人が読んだ時刻
-- どちらのユーザーを消しても一緒に消える（ON DELETE CASCADE）
CREATE TABLE direct_messages (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    sender_id    VARCHAR(36)   NOT NULL,
    recipient_id VARCHAR(36)   NOT NULL,
    body         VARCHAR(4000) NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    read_at      DATETIME(6)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_direct_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_direct_messages_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE
);
-- 2人の間のメッセージを新しい順に読むため（送った分と受け取った分の両方を引く）
CREATE INDEX idx_direct_messages_sender_recipient_id ON direct_messages (sender_id, recipient_id, id);
CREATE INDEX idx_direct_messages_recipient_sender_id ON direct_messages (recipient_id, sender_id, id);
