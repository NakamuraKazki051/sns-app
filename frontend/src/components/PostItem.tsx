import { useCallback, useState } from "react";
import { Link, useNavigate } from "react-router";
import { api, errorMessage } from "../api";
import { useMe } from "../auth";
import { useFeedback } from "../feedback";
import { TextWithHashtags } from "../hashtags";
import { countChars, fullTime, POST_MAX, relativeTime } from "../text";
import type { Post } from "../types";
import { Avatar } from "./Avatar";
import { ConfirmDialog } from "./ConfirmDialog";
import { ImageViewer } from "./ImageViewer";

type Props = {
  post: Post;
  /** いいねなどで中身が変わったとき */
  onChange: (post: Post) => void;
  onDeleted: (id: string) => void;
  /** 一覧では本文を押すと詳細ページへ。詳細ページ自身では移らない */
  linkToDetail?: boolean;
  /** 新着として読み込んだばかりの投稿。少しの間だけ色を付けて、どこまでが新しいか分かるようにする */
  isNew?: boolean;
};

export function PostItem({ post, onChange, onDeleted, linkToDetail = true, isNew = false }: Props) {
  const me = useMe();
  const navigate = useNavigate();
  const { showError } = useFeedback();
  const [liking, setLiking] = useState(false);
  const [reposting, setReposting] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [viewing, setViewing] = useState(false);
  // 本文を直している途中の文(F-19)。null なら直していない
  const [draft, setDraft] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const closeViewer = useCallback(() => setViewing(false), []);
  const detailPath = `/posts/${post.id}`;
  const mine = post.user.id === me.id;

  async function toggleRepost() {
    if (reposting) return;
    setReposting(true);
    try {
      const result = await api<{ reposted: boolean; repostsCount: number }>(`/api/posts/${post.id}/repost`, {
        method: post.repostedByMe ? "DELETE" : "POST",
      });
      onChange({ ...post, repostedByMe: result.reposted, repostsCount: result.repostsCount });
    } catch (e) {
      showError(errorMessage(e));
    } finally {
      setReposting(false);
    }
  }

  async function toggleLike() {
    if (liking) return;
    setLiking(true);
    try {
      const result = await api<{ liked: boolean; likesCount: number }>(`/api/posts/${post.id}/like`, {
        method: post.likedByMe ? "DELETE" : "POST",
      });
      onChange({ ...post, likedByMe: result.liked, likesCount: result.likesCount });
    } catch (e) {
      showError(errorMessage(e));
    } finally {
      setLiking(false);
    }
  }

  async function remove() {
    setConfirming(false);
    try {
      await api<void>(`/api/posts/${post.id}`, { method: "DELETE" });
      onDeleted(post.id);
    } catch (e) {
      showError(errorMessage(e));
    }
  }

  async function saveEdit() {
    if (draft === null || saving) return;
    const text = draft.trim();
    if (text === "" && !post.image) {
      showError("投稿の本文を入力してください");
      return;
    }
    if (countChars(text) > POST_MAX) {
      showError(`投稿は${POST_MAX}文字以内で入力してください`);
      return;
    }
    setSaving(true);
    try {
      const saved = await api<Post>(`/api/posts/${post.id}`, { method: "PATCH", body: { body: draft } });
      // リポストとして出している行なら、リポストした人の表示はそのまま残す
      onChange({ ...saved, repostedBy: post.repostedBy });
      setDraft(null);
    } catch (e) {
      showError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  }

  // 本文の余白を押したら詳細へ。ただし文字を選んでいるとき(コピーしたいとき)は移らない
  function openDetail() {
    if (linkToDetail && !window.getSelection()?.toString()) navigate(detailPath);
  }

  return (
    <li className={["post", linkToDetail && "clickable", isNew && "new"].filter(Boolean).join(" ")} onClick={openDetail}>
      <Link to={`/@${post.user.username}`} onClick={(e) => e.stopPropagation()}>
        <Avatar user={post.user} />
      </Link>
      <div className="post-body">
        {post.repostedBy && (
          <p className="repost-label">
            🔁{" "}
            {post.repostedBy.id === me.id ? (
              "リポストしました"
            ) : (
              <>
                <Link to={`/@${post.repostedBy.username}`} onClick={(e) => e.stopPropagation()}>
                  {post.repostedBy.displayName}
                </Link>
                さんがリポスト
              </>
            )}
          </p>
        )}
        <div className="post-meta">
          <Link to={`/@${post.user.username}`} className="name" onClick={(e) => e.stopPropagation()}>
            {post.user.displayName}
          </Link>
          <span className="sub">@{post.user.username}</span>
          <span className="sub">·</span>
          <Link to={detailPath} className="sub" title={fullTime(post.createdAt)} onClick={(e) => e.stopPropagation()}>
            <time dateTime={post.createdAt}>{relativeTime(post.createdAt)}</time>
          </Link>
          {post.editedAt && (
            <span className="sub" title={`${fullTime(post.editedAt)} に編集`}>
              (編集済み)
            </span>
          )}
        </div>
        {draft !== null ? (
          <div className="post-edit" onClick={(e) => e.stopPropagation()}>
            <textarea aria-label="投稿を編集" value={draft} onChange={(e) => setDraft(e.target.value)} autoFocus />
            <div className="post-edit-actions">
              <span className={countChars(draft.trim()) > POST_MAX ? "counter over" : "counter"}>
                {POST_MAX - countChars(draft.trim())}
              </span>
              <button type="button" className="button" disabled={saving} onClick={() => setDraft(null)}>
                キャンセル
              </button>
              <button type="button" className="button primary" disabled={saving} onClick={saveEdit}>
                保存する
              </button>
            </div>
          </div>
        ) : (
          // 本文は文字として出す(HTML として解釈しない)。#タグだけリンクにする。改行は CSS の pre-wrap でそのまま見せる
          post.body !== "" && (
            <p className="post-text">
              <TextWithHashtags text={post.body} />
            </p>
          )
        )}
        {post.image && (
          // 幅と高さを先に渡して枠を取っておく(読み込んだときに下の投稿がずれない)。押すと大きく見られる
          <button
            type="button"
            className="post-image"
            aria-label="画像を大きく見る"
            onClick={(e) => {
              e.stopPropagation();
              setViewing(true);
            }}
          >
            <img
              src={post.image.url}
              width={post.image.width}
              height={post.image.height}
              style={{ aspectRatio: `${post.image.width} / ${post.image.height}` }}
              alt={post.body === "" ? `${post.user.displayName}さんの画像` : ""}
              loading="lazy"
              decoding="async"
            />
          </button>
        )}
        <div className="post-actions" onClick={(e) => e.stopPropagation()}>
          <Link to={detailPath} className="action" aria-label={`リプライ ${post.repliesCount}件`}>
            💬 <span>{post.repliesCount}</span>
          </Link>
          {/* 自分の投稿はリポストできない(数だけ見せる) */}
          <button
            type="button"
            className={["action", post.repostedByMe && "reposted", mine && "unavailable"].filter(Boolean).join(" ")}
            aria-pressed={post.repostedByMe}
            aria-label={post.repostedByMe ? "リポストを取り消す" : "リポスト"}
            title={mine ? "自分の投稿はリポストできません" : undefined}
            disabled={mine || reposting}
            onClick={toggleRepost}
          >
            🔁 <span>{post.repostsCount}</span>
          </button>
          <button
            type="button"
            className={post.likedByMe ? "action liked" : "action"}
            aria-pressed={post.likedByMe}
            aria-label={post.likedByMe ? "いいねを取り消す" : "いいね"}
            disabled={liking}
            onClick={toggleLike}
          >
            {post.likedByMe ? "♥" : "♡"} <span>{post.likesCount}</span>
          </button>
          {mine && draft === null && (
            <button type="button" className="action" onClick={() => setDraft(post.body)}>
              編集
            </button>
          )}
          {mine && (
            <button type="button" className="action delete" onClick={() => setConfirming(true)}>
              削除
            </button>
          )}
        </div>
      </div>
      {viewing && post.image && (
        <div onClick={(e) => e.stopPropagation()}>
          <ImageViewer src={post.image.url} alt={post.body || `${post.user.displayName}さんの画像`} onClose={closeViewer} />
        </div>
      )}
      {confirming && (
        <div onClick={(e) => e.stopPropagation()}>
          <ConfirmDialog
            title="投稿を削除しますか？"
            message="削除すると元に戻せません。リプライ・いいね・リポストも消えます。"
            confirmLabel="削除する"
            onConfirm={remove}
            onCancel={() => setConfirming(false)}
          />
        </div>
      )}
    </li>
  );
}
