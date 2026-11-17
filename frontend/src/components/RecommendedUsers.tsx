import { useEffect, useState } from "react";
import { Link } from "react-router";
import { api } from "../api";
import type { RecommendedUser as Recommended } from "../api-schema";
import { Avatar } from "./Avatar";

export type RecommendedUser = Recommended;

function reasonText(user: RecommendedUser): string {
  switch (user.reason) {
    case "FOLLOWED_BY_FOLLOWING":
      return `フォロー中の${user.mutualCount}人がフォロー`;
    case "POPULAR":
      return "人気のユーザー";
    case "NEW":
      return "最近はじめた人";
  }
}

/** おすすめのユーザー(F-27)。フォロー中の人がよくフォローしている人など5人まで。押すとその人のページへ */
export function RecommendedUsers() {
  const [users, setUsers] = useState<RecommendedUser[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    api<RecommendedUser[]>("/api/recommendations/users")
      .then((list) => !cancelled && setUsers(list ?? []))
      .catch(() => !cancelled && setUsers([]));
    return () => {
      cancelled = true;
    };
  }, []);

  if (!users || users.length === 0) return null;
  return (
    <section className="card recommended-users" aria-label="おすすめのユーザー">
      <h3>おすすめのユーザー</h3>
      <ul className="post-list">
        {users.map((user) => (
          <li key={user.id}>
            <Link to={`/@${user.username}`} className="user-row">
              <Avatar user={user} />
              <div className="post-body">
                <div className="post-meta">
                  <span className="name">{user.displayName}</span>
                  <span className="sub">@{user.username}</span>
                </div>
                <p className="sub recommend-reason">{reasonText(user)}</p>
                {user.bio && <p className="user-bio">{user.bio}</p>}
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
