import { Link } from "react-router";
import type { UserSummary } from "../types";
import type { Paged } from "../usePaged";
import { Avatar } from "./Avatar";
import { MoreButton } from "./MoreButton";

/** 検索結果・フォロー一覧 */
export function UserList({ paged, emptyMessage }: { paged: Paged<UserSummary>; emptyMessage: string }) {
  return (
    <>
      <ul className="post-list">
        {paged.items.map((user) => (
          <li key={user.id}>
            <Link to={`/@${user.username}`} className="user-row">
              <Avatar user={user} />
              <div className="post-body">
                <div className="post-meta">
                  <span className="name">{user.displayName}</span>
                  <span className="sub">@{user.username}</span>
                </div>
                {user.bio && <p className="user-bio">{user.bio}</p>}
              </div>
            </Link>
          </li>
        ))}
      </ul>
      <MoreButton
        isEmpty={paged.items.length === 0}
        hasMore={paged.hasMore}
        loading={paged.loading}
        error={paged.error}
        onMore={paged.loadMore}
        onRetry={paged.retry}
        emptyMessage={emptyMessage}
      />
    </>
  );
}
