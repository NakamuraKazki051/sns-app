import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { api, ApiError, errorMessage } from "../api";
import { useMe } from "../auth";
import { Avatar } from "../components/Avatar";
import { PostList } from "../components/PostList";
import { useFeedback } from "../feedback";
import { loadPosts } from "../loaders";
import type { Post, Profile } from "../types";
import { usePaged } from "../usePaged";
import { NotFoundPage } from "./NotFoundPage";

/** URL の「@ユーザー名」からユーザー名を取り出す。形が違えば null */
export function usernameFromHandle(handle: string | undefined): string | null {
  const match = /^@([A-Za-z0-9_]{1,15})$/.exec(handle ?? "");
  return match ? match[1] : null;
}

/** ユーザーページ(/@ユーザー名): プロフィール、フォロー、投稿一覧 */
export function UserPage() {
  const username = usernameFromHandle(useParams().handle);
  if (!username) return <NotFoundPage message="ユーザーが見つかりません" />;
  // ユーザーが変わったら中身を作り直す(前の人の表示が一瞬残らないように)
  return <UserPageContent key={username.toLowerCase()} username={username} />;
}

function UserPageContent({ username }: { username: string }) {
  const me = useMe();
  const { showError } = useFeedback();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [following, setFollowing] = useState(false);
  const postsPath = `/api/users/${username}/posts`;
  const posts = usePaged<Post>(postsPath, loadPosts(postsPath));

  useEffect(() => {
    let cancelled = false;
    api<Profile>(`/api/users/${username}`)
      .then((loaded) => !cancelled && setProfile(loaded))
      .catch((e) => {
        if (cancelled) return;
        if (e instanceof ApiError && e.status === 404) setNotFound(true);
        else showError(errorMessage(e));
      });
    return () => {
      cancelled = true;
    };
  }, [username, showError]);

  if (notFound) return <NotFoundPage message={`ユーザーが見つかりません: @${username}`} />;
  if (!profile) return <p className="empty">読み込み中…</p>;

  const isMe = profile.id === me.id;

  async function toggleFollow() {
    if (!profile || following) return;
    setFollowing(true);
    try {
      const result = await api<{ following: boolean; followersCount: number }>(
        `/api/users/${profile.username}/follow`,
        { method: profile.followedByMe ? "DELETE" : "POST" },
      );
      setProfile({ ...profile, followedByMe: result.following, followersCount: result.followersCount });
    } catch (e) {
      showError(errorMessage(e));
    } finally {
      setFollowing(false);
    }
  }

  return (
    <>
      <div className="card profile">
        <div className="profile-top">
          <Avatar user={profile} large />
          {isMe ? (
            <Link to="/settings" className="button">
              プロフィールを編集
            </Link>
          ) : (
            <button
              type="button"
              className={profile.followedByMe ? "button following" : "button primary"}
              disabled={following}
              onClick={toggleFollow}
              aria-pressed={profile.followedByMe}
            >
              {profile.followedByMe ? "フォロー中" : "フォローする"}
            </button>
          )}
        </div>
        <h2>{profile.displayName}</h2>
        <p className="muted">@{profile.username}</p>
        {profile.bio && <p className="profile-bio">{profile.bio}</p>}
        <p className="profile-stats">
          <span>
            <strong>{profile.postsCount}</strong> 件の投稿
          </span>
          <Link to={`/@${profile.username}/following`}>
            <strong>{profile.followingCount}</strong> フォロー中
          </Link>
          <Link to={`/@${profile.username}/followers`}>
            <strong>{profile.followersCount}</strong> フォロワー
          </Link>
        </p>
      </div>
      <PostList
        paged={posts}
        emptyMessage="まだ投稿がありません"
        onDeleted={() => setProfile((p) => (p ? { ...p, postsCount: Math.max(0, p.postsCount - 1) } : p))}
      />
    </>
  );
}
