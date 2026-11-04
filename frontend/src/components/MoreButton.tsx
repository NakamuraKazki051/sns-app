import { useEffect, useRef } from "react";

type Props = {
  isEmpty: boolean;
  hasMore: boolean;
  loading: boolean;
  error: string | null;
  onMore: () => void;
  onRetry: () => void;
  emptyMessage: string;
};

/**
 * 一覧の下: 続きの読み込み、読み込み中、空のとき、読み込みに失敗したとき。
 * 「もっと見る」ボタンが画面に近づいたら自動で続きを読む(無限スクロール)。
 * ボタンも残すので、自動で読まない環境やキーボードでも続きを読める
 */
export function MoreButton({ isEmpty, hasMore, loading, error, onMore, onRetry, emptyMessage }: Props) {
  const buttonRef = useRef<HTMLButtonElement>(null);
  const onMoreRef = useRef(onMore);
  onMoreRef.current = onMore;
  const showButton = hasMore && !loading && !error && !isEmpty;

  useEffect(() => {
    const button = buttonRef.current;
    if (!showButton || !button || typeof IntersectionObserver === "undefined") return;
    // 画面の下端より少し手前(300px)で読み始め、スクロールが止まらないようにする
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) onMoreRef.current();
      },
      { rootMargin: "0px 0px 300px 0px" },
    );
    observer.observe(button);
    return () => observer.disconnect();
  }, [showButton]);

  if (error && !loading) {
    return (
      <div className="empty">
        <p>{error}</p>
        <button type="button" className="button more" onClick={onRetry}>
          もう一度読み込む
        </button>
      </div>
    );
  }
  if (loading) return <p className="empty">読み込み中…</p>;
  if (isEmpty) return <p className="empty">{emptyMessage}</p>;
  if (!hasMore) return null;
  return (
    <button type="button" className="button more" ref={buttonRef} onClick={onMore}>
      もっと見る
    </button>
  );
}
