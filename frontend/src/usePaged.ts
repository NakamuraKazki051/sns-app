import { useCallback, useEffect, useRef, useState } from "react";
import { errorMessage } from "./api";

export type Page<T> = { items: T[]; next: string | null };

export type Paged<T extends { id: string }> = ReturnType<typeof usePaged<T>>;

/**
 * 「もっと見る」で続きを読む一覧。cursor は前のページの最後の目印(投稿 ID など)。
 * 画面で足した項目(新しい投稿など)と目印は別に持つので、足しても続きの位置はずれない。
 * key が変わったら(別のユーザーページに移ったなど)読み直し、前の画面の応答が後から届いても捨てる
 */
export function usePaged<T extends { id: string }>(
  key: string,
  fetchPage: (cursor: string | null) => Promise<Page<T>>,
) {
  const [items, setItems] = useState<T[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const generation = useRef(0);
  const fetchRef = useRef(fetchPage);
  fetchRef.current = fetchPage;

  // 失敗したときに同じ読み込みをやり直すため、最後に読もうとした位置を覚えておく
  const lastAttempt = useRef<{ from: string | null; replace: boolean }>({ from: null, replace: true });

  const load = useCallback(async (from: string | null, replace: boolean) => {
    lastAttempt.current = { from, replace };
    const current = generation.current;
    setLoading(true);
    setError(null);
    try {
      const page = await fetchRef.current(from);
      if (current !== generation.current) return;
      setItems((prev) => {
        if (replace) return page.items;
        // 読んでいる間に削除などでずれても、同じ項目を2回出さない
        const seen = new Set(prev.map((item) => item.id));
        return [...prev, ...page.items.filter((item) => !seen.has(item.id))];
      });
      setCursor(page.next);
      setHasMore(page.next !== null);
    } catch (e) {
      if (current === generation.current) setError(errorMessage(e));
    } finally {
      if (current === generation.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    generation.current++;
    setItems([]);
    setHasMore(false);
    void load(null, true);
  }, [key, load]);

  const loadMore = useCallback(() => {
    if (!loading && cursor !== null) void load(cursor, false);
  }, [cursor, load, loading]);

  const reload = useCallback(() => {
    generation.current++;
    void load(null, true);
  }, [load]);

  const retry = useCallback(() => {
    void load(lastAttempt.current.from, lastAttempt.current.replace);
  }, [load]);

  return { items, setItems, hasMore, loading, error, loadMore, reload, retry };
}
