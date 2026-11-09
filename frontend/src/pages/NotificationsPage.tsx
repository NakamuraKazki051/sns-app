import { useEffect } from "react";
import { Link, useOutletContext } from "react-router";
import { api } from "../api";
import { Avatar } from "../components/Avatar";
import { MoreButton } from "../components/MoreButton";
import { fullTime, relativeTime } from "../text";
import type { Notification } from "../types";
import { usePaged, type Page } from "../usePaged";

const ACTIONS: Record<Notification["type"], string> = {
  LIKE: "さんがあなたの投稿にいいねしました",
  REPLY: "さんがあなたの投稿にリプライしました",
  REPOST: "さんがあなたの投稿をリポストしました",
  FOLLOW: "さんがあなたをフォローしました",
};

/** Layout が渡す。既読にしたら、メニューの未読の数を消す */
export type NotificationsOutlet = { onNotificationsRead: () => void };

export async function loadNotifications(cursor: string | null): Promise<Page<Notification>> {
  const page = await api<{ notifications: Notification[]; nextCursor: string | null }>(
    cursor ? `/api/notifications?before=${encodeURIComponent(cursor)}` : "/api/notifications",
  );
  return { items: page.notifications, next: page.nextCursor };
}

/** 通知の一覧(F-21)。開いたら、すべて既読にする(開く前に未読だったものは色を付けて出す) */
export function NotificationsPage() {
  const outlet = useOutletContext<NotificationsOutlet | undefined>();
  const paged = usePaged<Notification>("/api/notifications", loadNotifications);
  const onRead = outlet?.onNotificationsRead;

  useEffect(() => {
    api<void>("/api/notifications/read", { method: "POST" })
      .then(() => onRead?.())
      .catch(() => {
        // 既読にできなくても一覧は見られる。次に開いたときにまた既読にする
      });
  }, [onRead]);

  return (
    <>
      <h2 className="screen-title">通知</h2>
      <ul className="post-list">
        {paged.items.map((n) => (
          <li key={n.id} className={n.read ? "notification" : "notification unread"}>
            <Link to={`/@${n.actor.username}`}>
              <Avatar user={n.actor} />
            </Link>
            <div>
              <p>
                <Link to={`/@${n.actor.username}`} className="name">
                  {n.actor.displayName}
                </Link>
                {ACTIONS[n.type]}
                <span className="sub" title={fullTime(n.createdAt)}>
                  {" · "}
                  {relativeTime(n.createdAt)}
                </span>
              </p>
              {n.post && (
                <Link to={`/posts/${n.post.id}`} className="notification-post">
                  {n.post.body || "(画像の投稿)"}
                </Link>
              )}
            </div>
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
        emptyMessage="まだ通知はありません"
      />
    </>
  );
}
