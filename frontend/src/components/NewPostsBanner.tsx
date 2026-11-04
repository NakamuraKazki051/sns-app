import type { NewPosts } from "../types";
import { Avatar } from "./Avatar";

type Props = {
  newPosts: NewPosts;
  onClick: () => void;
};

/** 一覧の上に浮かぶ「↑ 新しい投稿 N件」。押すと先頭へ戻って新着を読み込む */
export function NewPostsBanner({ newPosts, onClick }: Props) {
  const count = newPosts.count > 99 ? "99+" : String(newPosts.count);
  return (
    <div className="new-posts-anchor">
      <button type="button" className="new-posts-banner" onClick={onClick} aria-live="polite">
        <span aria-hidden="true">↑</span>
        <span className="new-posts-avatars">
          {newPosts.users.map((user) => (
            <Avatar key={user.id} user={user} />
          ))}
        </span>
        新しい投稿 {count}件
      </button>
    </div>
  );
}
