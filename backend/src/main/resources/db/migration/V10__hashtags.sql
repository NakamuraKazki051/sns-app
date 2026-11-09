-- ハッシュタグ（F-22）。投稿の本文にある「#タグ」を1つずつ持つ。tag は小文字にそろえ、# は付けない
-- 投稿を直すと付け直し、投稿を消すと一緒に消える。created_at は投稿した時刻（トレンドを数える範囲に使う）
-- 前からある投稿のタグは、次の V11（Java）で本文から取り出す
CREATE TABLE post_hashtags (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    post_id    BIGINT      NOT NULL,
    tag        VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_post_hashtags_post_id_tag UNIQUE (post_id, tag),
    CONSTRAINT fk_post_hashtags_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE
);
-- タグの投稿を新しい順に読む・最近のタグを数えるため
CREATE INDEX idx_post_hashtags_tag_post_id ON post_hashtags (tag, post_id);
CREATE INDEX idx_post_hashtags_created_at ON post_hashtags (created_at);
