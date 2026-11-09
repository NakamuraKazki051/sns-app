-- 投稿の編集（F-19）。本文を最後に直した時刻。直していなければ null で、画面は「編集済み」を出さない
-- 直す前の本文は残さない
ALTER TABLE posts ADD COLUMN edited_at DATETIME(6) NULL;
