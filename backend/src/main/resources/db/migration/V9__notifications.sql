-- 通知（F-21）。いいね・リプライ・リポスト・フォローされたら、された人に1件
-- type: LIKE / REPLY / REPOST / FOLLOW。post_id は FOLLOW 以外で入る（どの投稿への反応か）
-- 取り消された（いいねを外した・フォローを外したなど）ら消す。読んだら read_at が入る
-- 投稿・ユーザーを消すと一緒に消える（ON DELETE CASCADE）
CREATE TABLE notifications (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    recipient_id VARCHAR(36) NOT NULL,
    actor_id     VARCHAR(36) NOT NULL,
    type         VARCHAR(10) NOT NULL,
    post_id      BIGINT      NULL,
    created_at   DATETIME(6) NOT NULL,
    read_at      DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE
);
-- 自分の通知を新しい順に読む・未読を数えるため
CREATE INDEX idx_notifications_recipient_id_id ON notifications (recipient_id, id);
