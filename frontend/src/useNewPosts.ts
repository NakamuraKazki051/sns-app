import { useCallback, useEffect, useState } from "react";
import { api } from "./api";
import type { NewPosts, Post } from "./types";

/** 新着を確かめる間隔 */
export const POLL_INTERVAL_MS = 30_000;

/**
 * 一覧の中で一番新しい投稿の ID(リポストの行は元の投稿が古いことがあるので除く)。
 * ID は桁が大きくなりうるので、数値ではなく BigInt で比べる。空なら "0"
 */
export function latestPostId(posts: Post[]): string {
  let latest = 0n;
  for (const post of posts) {
    if (post.repostedBy) continue;
    const id = BigInt(post.id);
    if (id > latest) latest = id;
  }
  return latest.toString();
}

/** a の方が新しい投稿の ID か */
export function isNewerId(a: string, b: string): boolean {
  return BigInt(a) > BigInt(b);
}

/**
 * 今見ている一覧より新しい、他の人の投稿があるかを30秒ごとに確かめる(新着のお知らせ用)。
 * 自動で一覧に足すと読んでいる途中で投稿がずれるので、数だけ知らせて、読み込むかは本人が決める。
 * タブが裏にあるときは聞きに行かず、表に戻ったときにすぐ確かめる。
 * after(一覧の先頭の ID)が変わったら(読み直した、自分で投稿した)、お知らせを消して数え直す
 */
export function useNewPosts(path: string, after: string | null) {
  const [newPosts, setNewPosts] = useState<NewPosts | null>(null);

  useEffect(() => {
    setNewPosts(null);
    if (after === null) return;
    let cancelled = false;
    let running = false;

    async function check() {
      if (running || document.visibilityState !== "visible") return;
      running = true;
      try {
        const result = await api<NewPosts>(`${path}?after=${encodeURIComponent(after!)}`);
        if (!cancelled) setNewPosts(result.count > 0 ? result : null);
      } catch {
        // お知らせは無くても困らないので、失敗しても何も出さない(次の回にまた確かめる)
      } finally {
        running = false;
      }
    }

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
  }, [path, after]);

  const clear = useCallback(() => setNewPosts(null), []);
  return { newPosts, clear };
}
