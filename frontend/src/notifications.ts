import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "./api";
import { POLL_INTERVAL_MS } from "./useNewPosts";

/**
 * 未読の数(通知 F-21・DM F-23)。新着のお知らせと同じく30秒ごとに確かめ、タブが裏にあるときは聞きに行かない。
 * reset は 0 にする(通知の画面ですべて既読にしたとき)。refresh はすぐ聞き直す(DM の会話を開いて一部だけ既読にしたとき)
 */
export function useUnreadCount(path: string) {
  const [count, setCount] = useState(0);
  const checkRef = useRef<() => Promise<void>>(async () => {});

  useEffect(() => {
    let cancelled = false;
    let running = false;

    async function check() {
      if (running || document.visibilityState !== "visible") return;
      running = true;
      try {
        const result = await api<{ count: number }>(path);
        if (!cancelled) setCount(result.count);
      } catch {
        // 数が出なくても困らないので、失敗しても何もしない(次の回にまた確かめる)
      } finally {
        running = false;
      }
    }

    checkRef.current = check;
    void check();
    const timer = window.setInterval(check, POLL_INTERVAL_MS);
    const onVisible = () => {
      if (document.visibilityState === "visible") void check();
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [path]);

  const reset = useCallback(() => setCount(0), []);
  const refresh = useCallback(() => void checkRef.current(), []);
  return { count, reset, refresh };
}

export function useUnreadNotifications() {
  return useUnreadCount("/api/notifications/unread-count");
}

export function useUnreadMessages() {
  return useUnreadCount("/api/dm/unread-count");
}
