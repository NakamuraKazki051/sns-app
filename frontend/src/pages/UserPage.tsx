import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { api, ApiError, errorMessage } from "../api";
import { useMe } from "../auth";
import { Avatar } from "../components/Avatar";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { LoadFailed } from "../components/LoadFailed";
import { PostList } from "../components/PostList";
import { useFeedback } from "../feedback";
import { loadPosts, postKey } from "../loaders";
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
  const [loadError, setLoadError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [following, setFollowing] = useState(false);
  // ブロック・ミュート(F-20)。ブロックを変えたら、投稿の一覧も読み直す
  const [relating, setRelating] = useState(false);
  const [confirmingBlock, setConfirmingBlock] = useState(false);
  const [version, setVersion] = useState(0);
  const postsPath = `/api/users/${username}/posts`;
  const posts = usePaged<Post>(`${postsPath}#${version}`, loadPosts(postsPath), postKey);

  useEffect(() => {
    let cancelled = false;
    setLoadError(null);
    api<Profile>(`/api/users/${username}`)
      .then((loaded) => !cancelled && setProfile(loaded))
      .catch((e) => {
        if (cancelled) return;
        if (e instanceof ApiError && e.status === 404) setNotFound(true);
        else setLoadError(errorMessage(e));
      });
    return () => {
      cancelled = true;
    };
  }, [username, attempt]);

  if (notFound) return <NotFoundPage message={`ユーザーが見つかりません: @${username}`} />;
  if (loadError) return <LoadFailed message={loadError} onRetry={() => setAttempt((n) => n + 1)} />;
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

  async function toggleMute() {
    if (!profile || relating) return;
    setRelating(true);
    try {
      const result = await api<{ muting: boolean }>(`/api/users/${profile.username}/mute`, {
        method: profile.muting ? "DELETE" : "POST",
      });
      setProfile({ ...profile, muting: result.muting });
    } catch (e) {
      showError(errorMessage(e));
    } finally {
      setRelating(false);
    }
  }

  async function changeBlock(block: boolean) {
    if (!profile || relating) return;
    setConfirmingBlock(false);
    setRelating(true);
    try {
      await api<{ blocking: boolean }>(`/api/users/${profile.username}/block`, { method: block ? "POST" : "DELETE" });
      // フォローが外れて数も変わるので、プロフィールと投稿を読み直す
      setAttempt((n) => n + 1);
      setVersion((n) => n + 1);
    } catch (e) {
      showError(errorMessage(e));
    } finally {
      setRelating(false);
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
            <div className="profile-buttons">
              {!profile.blocking && (
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
              {!profile.blocking && (
                <Link to={`/messages/${profile.username}`} className="button">
                  メッセージ
                </Link>
              )}
              <button type="button" className="button" disabled={relating} onClick={toggleMute} aria-pressed={profile.muting}>
                {profile.muting ? "ミュート中" : "ミュート"}
              </button>
              <button
                type="button"
                className={profile.blocking ? "button danger" : "button"}
                disabled={relating}
                aria-pressed={profile.blocking}
                onClick={() => (profile.blocking ? changeBlock(false) : setConfirmingBlock(true))}
              >
                {profile.blocking ? "ブロック中" : "ブロック"}
              </button>
            </div>
          )}
        </div>
        {confirmingBlock && (
          <ConfirmDialog
            title={`@${profile.username} さんをブロックしますか？`}
            message="お互いのフォローが外れ、お互いの投稿・リプライ・プロフィールが見えなくなります。"
            confirmLabel="ブロックする"
            onConfirm={() => changeBlock(true)}
            onCancel={() => setConfirmingBlock(false)}
          />
        )}
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
        emptyMessage={profile.blocking ? "ブロック中のため、投稿は表示しません" : "まだ投稿がありません"}
        // 他の人のページでも、その人がリポストした自分の投稿は消せる。その人の投稿数は変わらない
        onDeleted={() => {
          if (isMe) setProfile((p) => (p ? { ...p, postsCount: Math.max(0, p.postsCount - 1) } : p));
        }}
      />
    </>
  );
}
