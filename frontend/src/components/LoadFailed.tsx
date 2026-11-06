/** 画面の中身を読めなかったとき。読み込み中のまま止まらないよう、理由とやり直しのボタンを出す */
export function LoadFailed({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="empty">
      <p>{message}</p>
      <button type="button" className="button more" onClick={onRetry}>
        もう一度読み込む
      </button>
    </div>
  );
}
