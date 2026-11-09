-- ブロックとミュート（F-20）。どちらも「誰が・誰を」の組で、同じ組は1つだけ
-- アカウントを消すと、する側・される側どちらの行も一緒に消える（ON DELETE CASCADE）
--
-- ブロック：お互いの投稿・リプライ・プロフィールが見えなくなり、フォロー・リプライ・いいね・リポストもできない
CREATE TABLE blocks (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    blocker_id VARCHAR(36) NOT NULL,
    blocked_id VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_blocks_blocker_id_blocked_id UNIQUE (blocker_id, blocked_id),
    CONSTRAINT fk_blocks_blocker FOREIGN KEY (blocker_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_blocks_blocked FOREIGN KEY (blocked_id) REFERENCES users (id) ON DELETE CASCADE
);
-- 「この人をブロックしている人」を引くため
CREATE INDEX idx_blocks_blocked_id ON blocks (blocked_id);

-- ミュート：自分のタイムライン・新着・リプライ一覧から相手が消える。相手には分からず、プロフィールは見られる
CREATE TABLE mutes (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    muter_id   VARCHAR(36) NOT NULL,
    muted_id   VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_mutes_muter_id_muted_id UNIQUE (muter_id, muted_id),
    CONSTRAINT fk_mutes_muter FOREIGN KEY (muter_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_mutes_muted FOREIGN KEY (muted_id) REFERENCES users (id) ON DELETE CASCADE
);
