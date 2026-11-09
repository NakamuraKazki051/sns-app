import { useCallback, useEffect, useState } from "react";
import { api } from "./api";
import { POLL_INTERVAL_MS } from "./useNewPosts";

/**
 * 未読の通知の数(F-21)。新着のお知らせと同じく30秒ごとに確かめ、タブが裏にあるときは聞きに行かない。
 * 通知の画面を開いて既読にしたら reset で 0 にする
 */
export function useUnreadNotifications() {
  const [count, setCount] = useState(0);

  useEffect(() => {
    let cancelled = false;
    let running = false;

    async function check() {
      if (running || document.visibilityState !== "visible") return;
      running = true;
      try {
        const result = await api<{ count: number }>("/api/notifications/unread-count");
        if (!cancelled) setCount(result.count);
      } catch {
        // 数が出なくても困らないので、失敗しても何もしない(次の回にまた確かめる)
      } finally {
        running = false;
      }
    }

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
  }, []);

  const reset = useCallback(() => setCount(0), []);
  return { count, reset };
}
