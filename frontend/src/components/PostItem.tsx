import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { api, errorMessage } from "../api";
import { useMe } from "../auth";
import { useFeedback } from "../feedback";
import { fullTime, relativeTime } from "../text";
import type { Post } from "../types";
import { Avatar } from "./Avatar";
import { ConfirmDialog } from "./ConfirmDialog";

type Props = {
  post: Post;
  /** いいねなどで中身が変わったとき */
  onChange: (post: Post) => void;
  onDeleted: (id: string) => void;
  /** 一覧では本文を押すと詳細ページへ。詳細ページ自身では移らない */
  linkToDetail?: boolean;
};

export function PostItem({ post, onChange, onDeleted, linkToDetail = true }: Props) {
  const me = useMe();
  const navigate = useNavigate();
  const { showError } = useFeedback();
  const [liking, setLiking] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const detailPath = `/posts/${post.id}`;

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

  // 本文の余白を押したら詳細へ。ただし文字を選んでいるとき(コピーしたいとき)は移らない
  function openDetail() {
    if (linkToDetail && !window.getSelection()?.toString()) navigate(detailPath);
  }

  return (
    <li className={linkToDetail ? "post clickable" : "post"} onClick={openDetail}>
      <Link to={`/@${post.user.username}`} onClick={(e) => e.stopPropagation()}>
        <Avatar user={post.user} />
      </Link>
      <div className="post-body">
        <div className="post-meta">
          <Link to={`/@${post.user.username}`} className="name" onClick={(e) => e.stopPropagation()}>
            {post.user.displayName}
          </Link>
          <span className="sub">@{post.user.username}</span>
          <span className="sub">·</span>
          <Link to={detailPath} className="sub" title={fullTime(post.createdAt)} onClick={(e) => e.stopPropagation()}>
            <time dateTime={post.createdAt}>{relativeTime(post.createdAt)}</time>
          </Link>
        </div>
        {/* 本文は文字として出す(HTML として解釈しない)。改行は CSS の pre-wrap でそのまま見せる */}
        <p className="post-text">{post.body}</p>
        <div className="post-actions" onClick={(e) => e.stopPropagation()}>
          <Link to={detailPath} className="action" aria-label={`リプライ ${post.repliesCount}件`}>
            💬 <span>{post.repliesCount}</span>
          </Link>
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
          {post.user.id === me.id && (
            <button type="button" className="action delete" onClick={() => setConfirming(true)}>
              削除
            </button>
          )}
        </div>
      </div>
      {confirming && (
        <div onClick={(e) => e.stopPropagation()}>
          <ConfirmDialog
            title="投稿を削除しますか？"
            message="削除すると元に戻せません。リプライといいねも消えます。"
            confirmLabel="削除する"
            onConfirm={remove}
            onCancel={() => setConfirming(false)}
          />
        </div>
      )}
    </li>
  );
}
