-- 通報（docs/spec.md 4.11）。ユーザーか投稿を、理由を選んで運営に知らせる。
-- post_id があれば投稿の通報（target_user_id はその投稿者）。無ければユーザーの通報。
-- 管理者（app.admin.usernames）が一覧で見て、対応したら resolved_at を入れる（resolution は DISMISSED か POST_DELETED）。
-- post_body は通報した時点の本文の写し（投稿が直されたり消されたりしても、何を通報したか分かるように）。
-- 通報した人・された人を消すと通報も消える。投稿を消しても通報は残り、post_id だけ空になる
CREATE TABLE reports (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    reporter_id    VARCHAR(36)  NOT NULL,
    target_user_id VARCHAR(36)  NOT NULL,
    post_id        BIGINT       NULL,
    post_body      VARCHAR(2000) NULL,
    reason         VARCHAR(20)  NOT NULL,
    detail         VARCHAR(2000) NULL,
    created_at     DATETIME(6)  NOT NULL,
    resolved_at    DATETIME(6)  NULL,
    resolution     VARCHAR(20)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_target FOREIGN KEY (target_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE SET NULL
);
CREATE INDEX idx_reports_open ON reports (resolved_at, id);
CREATE INDEX idx_reports_reporter ON reports (reporter_id, target_user_id);
