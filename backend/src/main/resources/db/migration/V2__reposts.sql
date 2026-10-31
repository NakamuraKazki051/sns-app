-- リポスト（F-13、フェーズ4）。1人が同じ投稿をリポストできるのは1回だけ
-- 投稿を消すと、その投稿のリポストも消える（ON DELETE CASCADE）

CREATE TABLE reposts (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    post_id    BIGINT      NOT NULL,
    user_id    VARCHAR(36) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reposts_user_id_post_id UNIQUE (user_id, post_id),
    CONSTRAINT fk_reposts_post_id FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_reposts_user_id FOREIGN KEY (user_id) REFERENCES users (id)
);
-- ホームタイムライン・ユーザーページで「この人たちのリポストを新しい順に」読むため
CREATE INDEX idx_reposts_user_id_id ON reposts (user_id, id);
-- 投稿ごとのリポスト数を数えるため
CREATE INDEX idx_reposts_post_id ON reposts (post_id);
