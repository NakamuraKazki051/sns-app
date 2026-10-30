// 文字数は「見た目の1文字」で数える(絵文字も1字)。サーバーの BreakIterator と同じ数え方
const segmenter = new Intl.Segmenter("ja", { granularity: "grapheme" });

export function countChars(text: string): number {
  let count = 0;
  for (const _ of segmenter.segment(text)) {
    count++;
  }
  return count;
}

export const POST_MAX = 140;
export const BIO_MAX = 160;
export const DISPLAY_NAME_MAX = 50;

/**
 * 表示名の長さ。サーバーは Java の文字数(UTF-16 の単位)で50までと決めているので、画面も同じ数え方にする
 * (絵文字は1つで2以上になる。投稿・自己紹介の「見た目の1文字」とは違う)
 */
export function displayNameLength(text: string): number {
  return text.length;
}

/** 「たった今」「3分前」「5時間前」「10月28日」「2025年10月28日」 */
export function relativeTime(iso: string, now: number = Date.now()): string {
  const time = Date.parse(iso);
  const seconds = Math.max(0, (now - time) / 1000);
  if (seconds < 60) return "たった今";
  if (seconds < 3600) return `${Math.floor(seconds / 60)}分前`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}時間前`;
  const date = new Date(time);
  const sameYear = date.getFullYear() === new Date(now).getFullYear();
  return `${sameYear ? "" : `${date.getFullYear()}年`}${date.getMonth() + 1}月${date.getDate()}日`;
}

/** マウスを乗せたときに出す正確な日時 */
export function fullTime(iso: string): string {
  return new Date(iso).toLocaleString("ja-JP", { dateStyle: "medium", timeStyle: "short" });
}

/**
 * ログイン後に戻る先。自分のサイト内のパスだけ許す。
 * 「//evil.example」や「/\evil.example」はブラウザが外部サイトとして扱うので弾く(オープンリダイレクト対策)
 */
export function safeNext(next: string | null): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) {
    return "/";
  }
  return next;
}
