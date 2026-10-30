import { NavLink, useParams } from "react-router";
import { UserList } from "../components/UserList";
import { loadNothing, loadUsers } from "../loaders";
import type { UserSummary } from "../types";
import { usePaged } from "../usePaged";
import { NotFoundPage } from "./NotFoundPage";
import { usernameFromHandle } from "./UserPage";

/** フォロー中・フォロワーの一覧(/@ユーザー名/following、/@ユーザー名/followers) */
export function FollowListPage({ kind }: { kind: "following" | "followers" }) {
  const username = usernameFromHandle(useParams().handle);
  const path = `/api/users/${username}/${kind}`;
  const paged = usePaged<UserSummary>(path, username ? loadUsers(path, "before") : loadNothing);
  if (!username) return <NotFoundPage message="ユーザーが見つかりません" />;

  return (
    <>
      <h2 className="screen-title">@{username}</h2>
      <div className="tabs">
        <NavLink to={`/@${username}/following`} className={({ isActive }) => (isActive ? "tab active" : "tab")}>
          フォロー中
        </NavLink>
        <NavLink to={`/@${username}/followers`} className={({ isActive }) => (isActive ? "tab active" : "tab")}>
          フォロワー
        </NavLink>
      </div>
      {paged.error?.startsWith("ユーザーが見つかりません") ? (
        <NotFoundPage message={paged.error} />
      ) : (
        <UserList
          paged={paged}
          emptyMessage={kind === "following" ? "まだ誰もフォローしていません" : "まだフォロワーがいません"}
        />
      )}
    </>
  );
}
