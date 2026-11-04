-- 投稿の画像（F-14、フェーズ4）。1投稿に1枚までなので、posts に列を足す（画像が無い投稿は全部 null）
-- 画像本体は DB に入れず、ローカルはディスク、AWS は S3 に置く。image_key はその置き場所での名前
-- 幅と高さは、画像を読み込む前に枠の大きさを決めて、一覧がガタつかないようにするため

ALTER TABLE posts ADD COLUMN image_key VARCHAR(64) NULL;
ALTER TABLE posts ADD COLUMN image_content_type VARCHAR(20) NULL;
ALTER TABLE posts ADD COLUMN image_width INT NULL;
ALTER TABLE posts ADD COLUMN image_height INT NULL;
