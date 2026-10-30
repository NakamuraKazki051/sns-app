import { Link } from "react-router";

export function NotFoundPage({ message = "ページが見つかりません" }: { message?: string }) {
  return (
    <div className="card empty">
      <p>{message}</p>
      <p>
        <Link to="/">タイムラインに戻る</Link>
      </p>
    </div>
  );
}
