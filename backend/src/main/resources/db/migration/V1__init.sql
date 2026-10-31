-- 最初のテーブル一式（2026-10-31 時点）。
-- それまでは Hibernate がエンティティから自動で作っていた（ddl-auto: update）。そのとき MySQL にできたテーブルと
-- まったく同じになるよう、制約名も Hibernate が付けた名前のままにしている。
-- すでにテーブルがある DB では、Flyway はこのファイルを実行せず「V1 まで済み」として扱う（baseline-on-migrate）。
-- テーブルを変えるときは、このファイルは直さず V2__〜.sql を足す。

CREATE TABLE users (
    id              VARCHAR(36)   NOT NULL,
    created_at      DATETIME(6),
    display_name    VARCHAR(50)   NOT NULL,
    email           VARCHAR(255)  NOT NULL,
    icon_updated_at DATETIME(6),
    password_hash   VARCHAR(255)  NOT NULL,
    username        VARCHAR(15)   NOT NULL,
    bio             VARCHAR(2000),
    PRIMARY KEY (id),
    CONSTRAINT UK6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email),
    CONSTRAINT UKr43af9ap4edm43mmtq01oddj6 UNIQUE (username)
);

-- アイコン画像（1MB まで）。ユーザー1人に1つ
-- data の型は MySQL では MEDIUMBLOB、テストの H2 では VARBINARY（Hibernate がそれぞれで期待する型）。application.yml で決める
CREATE TABLE user_icons (
    user_id      VARCHAR(36)  NOT NULL,
    content_type VARCHAR(20)  NOT NULL,
    data         ${icon_data_type} NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_id)
);

CREATE TABLE posts (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    body       VARCHAR(2000) NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    user_id    VARCHAR(36)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT FK5lidm6cqbc7u4xhqpxm898qme FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_posts_user_id_id ON posts (user_id, id);

-- 投稿を消すと、その投稿へのリプライといいねも消える（ON DELETE CASCADE）
CREATE TABLE replies (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    body       VARCHAR(2000) NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    post_id    BIGINT        NOT NULL,
    user_id    VARCHAR(36)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT FKlpjxe9eiutj9ybkgketdk1o8s FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT FKn60t7po8l0rllye52xx25q4xx FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_replies_post_id_id ON replies (post_id, id);

CREATE TABLE likes (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    post_id    BIGINT      NOT NULL,
    user_id    VARCHAR(36) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_likes_user_id_post_id UNIQUE (user_id, post_id),
    CONSTRAINT FKnvx9seeqqyy71bij291pwiwrg FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT FKry8tnr4x2vwemv2bb0h5hyl0x FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE
);
CREATE INDEX idx_likes_post_id ON likes (post_id);

CREATE TABLE follows (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    created_at  DATETIME(6) NOT NULL,
    followee_id VARCHAR(36) NOT NULL,
    follower_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_follows_follower_id_followee_id UNIQUE (follower_id, followee_id),
    CONSTRAINT FKeo7hqi2bt2vdwk6mpu0w2ihyb FOREIGN KEY (followee_id) REFERENCES users (id),
    CONSTRAINT FKqnkw0cwwh6572nyhvdjqlr163 FOREIGN KEY (follower_id) REFERENCES users (id)
);
CREATE INDEX idx_follows_followee_id_id ON follows (followee_id, id);
