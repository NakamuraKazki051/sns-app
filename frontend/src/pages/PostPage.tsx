import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { api, ApiError, errorMessage } from "../api";
import { useMe } from "../auth";
import { Avatar } from "../components/Avatar";
import { Composer } from "../components/Composer";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { MoreButton } from "../components/MoreButton";
import { PostItem } from "../components/PostItem";
import { useFeedback } from "../feedback";
import { loadNothing, loadReplies } from "../loaders";
import { fullTime, relativeTime } from "../text";
import type { Post, Reply } from "../types";
import { usePaged } from "../usePaged";
import { NotFoundPage } from "./NotFoundPage";

/** 投稿の詳細: 投稿、いいね、リプライ欄とリプライ一覧(古い順) */
export function PostPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const { showError, showToast } = useFeedback();
  const [post, setPost] = useState<Post | null>(null);
  const [notFound, setNotFound] = useState(false);
  const validId = /^\d+$/.test(id);
  const replies = usePaged<Reply>(id, validId ? loadReplies(id) : loadNothing);

  useEffect(() => {
    if (!validId) return;
    let cancelled = false;
    setPost(null);
    setNotFound(false);
    api<Post>(`/api/posts/${id}`)
      .then((loaded) => !cancelled && setPost(loaded))
      .catch((e) => {
        if (cancelled) return;
        if (e instanceof ApiError && e.status === 404) setNotFound(true);
        else showError(errorMessage(e));
      });
    return () => {
      cancelled = true;
    };
  }, [id, validId, showError]);

  if (!validId || notFound) return <NotFoundPage message="投稿が見つかりません。削除された可能性があります" />;

  async function submitReply(text: string) {
    try {
      const reply = await api<Reply>(`/api/posts/${id}/replies`, { method: "POST", body: { body: text } });
      replies.setItems((list) => [...list, reply]);
      setPost((p) => (p ? { ...p, repliesCount: p.repliesCount + 1 } : p));
      showToast("リプライしました");
    } catch (e) {
      showError(errorMessage(e));
      throw e;
    }
  }

  function replyDeleted(replyId: string) {
    replies.setItems((list) => list.filter((r) => r.id !== replyId));
    setPost((p) => (p ? { ...p, repliesCount: Math.max(0, p.repliesCount - 1) } : p));
  }

  return (
    <>
      <h2 className="screen-title">投稿</h2>
      {post ? (
        <ul className="post-list">
          <PostItem
            post={post}
            linkToDetail={false}
            onChange={setPost}
            onDeleted={() => {
              showToast("投稿を削除しました");
              navigate("/", { replace: true });
            }}
          />
        </ul>
      ) : (
        <p className="empty">読み込み中…</p>
      )}
      <Composer placeholder="リプライを書く" submitLabel="リプライ" onSubmit={submitReply} />
      <ul className="post-list">
        {replies.items.map((reply) => (
          <ReplyItem key={reply.id} reply={reply} onDeleted={replyDeleted} />
        ))}
      </ul>
      <MoreButton
        isEmpty={replies.items.length === 0}
        hasMore={replies.hasMore}
        loading={replies.loading}
        error={replies.error}
        onMore={replies.loadMore}
        onRetry={replies.retry}
        emptyMessage="まだリプライがありません"
      />
    </>
  );
}

function ReplyItem({ reply, onDeleted }: { reply: Reply; onDeleted: (id: string) => void }) {
  const me = useMe();
  const { showError } = useFeedback();
  const [confirming, setConfirming] = useState(false);

  async function remove() {
    setConfirming(false);
    try {
      await api<void>(`/api/replies/${reply.id}`, { method: "DELETE" });
      onDeleted(reply.id);
    } catch (e) {
      showError(errorMessage(e));
    }
  }

  return (
    <li className="post reply">
      <Link to={`/@${reply.user.username}`}>
        <Avatar user={reply.user} />
      </Link>
      <div className="post-body">
        <div className="post-meta">
          <Link to={`/@${reply.user.username}`} className="name">
            {reply.user.displayName}
          </Link>
          <span className="sub">@{reply.user.username}</span>
          <span className="sub">·</span>
          <time className="sub" dateTime={reply.createdAt} title={fullTime(reply.createdAt)}>
            {relativeTime(reply.createdAt)}
          </time>
          {reply.user.id === me.id && (
            <button type="button" className="action delete post-delete" onClick={() => setConfirming(true)}>
              削除
            </button>
          )}
        </div>
        <p className="post-text">{reply.body}</p>
      </div>
      {confirming && (
        <ConfirmDialog
          title="リプライを削除しますか？"
          message="削除すると元に戻せません。"
          confirmLabel="削除する"
          onConfirm={remove}
          onCancel={() => setConfirming(false)}
        />
      )}
    </li>
  );
}
