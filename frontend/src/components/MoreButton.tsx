type Props = {
  isEmpty: boolean;
  hasMore: boolean;
  loading: boolean;
  error: string | null;
  onMore: () => void;
  onRetry: () => void;
  emptyMessage: string;
};

/** 一覧の下: 「もっと見る」、読み込み中、空のとき、読み込みに失敗したとき */
export function MoreButton({ isEmpty, hasMore, loading, error, onMore, onRetry, emptyMessage }: Props) {
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
    <button type="button" className="button more" onClick={onMore}>
      もっと見る
    </button>
  );
}
