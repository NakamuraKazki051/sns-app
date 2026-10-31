-- 性能テスト用のデータ（ユーザー1,000人・投稿10万件・リポスト約2万件）
-- 性能テスト専用の DB（snsapp_perf）に入れる。普段の DB には入れない。直接は実行せず、run.ps1 から実行する。
-- 前提: run.ps1 がアプリを起動してテーブルを作り、API で perf_0000 を登録済み。ほかのユーザーはそのパスワードを使い回す。

SET SESSION cte_max_recursion_depth = 100000;

-- 1〜100000 の連番（投稿を作るのに使う）
CREATE TABLE seq (n INT PRIMARY KEY);
INSERT INTO seq (n)
WITH RECURSIVE s (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < 100000)
SELECT n FROM s;

-- ユーザー: perf_0001〜perf_0999（perf_0000 と合わせて1,000人）
INSERT INTO users (id, email, password_hash, username, display_name, bio, created_at)
SELECT UUID(), CONCAT('perf_', LPAD(seq.n, 4, '0'), '@example.com'), main.password_hash,
       CONCAT('perf_', LPAD(seq.n, 4, '0')), CONCAT('性能テスト ', seq.n), '性能テスト用のユーザーです',
       NOW(6) - INTERVAL 200 DAY
FROM seq JOIN users main ON main.username = 'perf_0000'
WHERE seq.n < 1000;

-- フォローも投稿も少ない人。ホームタイムラインがいちばん遅くなる場合を測るために使う
INSERT INTO users (id, email, password_hash, username, display_name, created_at)
SELECT UUID(), 'perf_lonely@example.com', password_hash, 'perf_lonely', 'ひとりぼっち', NOW(6) - INTERVAL 200 DAY
FROM users WHERE username = 'perf_0000';

-- ユーザーに 0〜999 の番号を振る（perf_lonely は除く）
CREATE TABLE perf_users (no INT PRIMARY KEY, id VARCHAR(36) NOT NULL);
INSERT INTO perf_users (no, id)
SELECT CAST(SUBSTRING(username, 6) AS UNSIGNED), id FROM users WHERE username REGEXP '^perf_[0-9]{4}$';

-- 投稿10万件。古いものから1分おきで、投稿者はばらばらにする
INSERT INTO posts (user_id, body, created_at)
SELECT u.id, CONCAT('性能テストの投稿 #', seq.n, ' 🚀'), NOW(6) - INTERVAL (100000 - seq.n) MINUTE
FROM seq JOIN perf_users u ON u.no = (seq.n * 7) % 1000
ORDER BY seq.n;

-- perf_lonely はいちばん古い頃に1件だけ投稿した
INSERT INTO posts (user_id, body, created_at)
SELECT id, 'はじめての投稿', NOW(6) - INTERVAL 100001 MINUTE FROM users WHERE username = 'perf_lonely';

-- フォロー: 1人50人ずつ（計5万件）
INSERT INTO follows (follower_id, followee_id, created_at)
SELECT a.id, b.id, NOW(6) - INTERVAL 100 DAY
FROM perf_users a JOIN seq k ON k.n <= 50 JOIN perf_users b ON b.no = (a.no + k.n * 17) % 1000;

-- いいね: 投稿ごとに0〜6件（計約30万件）
INSERT INTO likes (user_id, post_id, created_at)
SELECT u.id, p.id, p.created_at
FROM posts p JOIN seq j ON j.n <= p.id % 7 JOIN perf_users u ON u.no = (p.id * 31 + j.n * 97) % 1000;

-- リプライ: 5件に1件の投稿に1件ずつ（計約2万件）
INSERT INTO replies (post_id, user_id, body, created_at)
SELECT p.id, u.id, '性能テストのリプライ', p.created_at
FROM posts p JOIN perf_users u ON u.no = (p.id * 13) % 1000
WHERE p.id % 5 = 0;

-- リポスト: 1人20件ずつ、ほかの人の投稿を（計約2万件）。リポストした時刻はばらばらにする
INSERT INTO reposts (user_id, post_id, created_at)
SELECT u.id, p.id, NOW(6) - INTERVAL ((u.no * 37 + k.n * 4999) % 100000) MINUTE
FROM perf_users u JOIN seq k ON k.n <= 20
JOIN posts p ON p.id = (u.no * 97 + k.n * 1009) % 100000 + 1
WHERE p.user_id <> u.id;

-- 作業用の表を消す（MySQL の一時テーブルは1つのクエリで2回使えないので、ふつうの表にしていた）
DROP TABLE seq, perf_users;

ANALYZE TABLE users, posts, follows, likes, replies, reposts;

SELECT (SELECT COUNT(*) FROM users) AS users, (SELECT COUNT(*) FROM posts) AS posts,
       (SELECT COUNT(*) FROM follows) AS follows, (SELECT COUNT(*) FROM likes) AS likes,
       (SELECT COUNT(*) FROM replies) AS replies, (SELECT COUNT(*) FROM reposts) AS reposts;
