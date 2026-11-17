-- DM の画像（F-23 の追加、docs/spec.md 4.9）。投稿の画像（V3）と同じく、本体は ImageStorage に置き、DB には名前と形式・幅・高さだけ。
-- 画像だけのメッセージは body が空文字になる
ALTER TABLE direct_messages ADD COLUMN image_key VARCHAR(64) NULL;
ALTER TABLE direct_messages ADD COLUMN image_content_type VARCHAR(20) NULL;
ALTER TABLE direct_messages ADD COLUMN image_width INT NULL;
ALTER TABLE direct_messages ADD COLUMN image_height INT NULL;
