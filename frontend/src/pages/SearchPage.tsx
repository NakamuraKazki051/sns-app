import { useEffect, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router";
import { api } from "../api";
import { UserList } from "../components/UserList";
import { hashtagPath } from "../hashtags";
import { loadNothing, loadUsers } from "../loaders";
import type { UserSummary } from "../types";
import { usePaged } from "../usePaged";

const QUERY_MAX = 50;

/** ユーザー検索(/search?q=…)。検索語は URL に入れるので、戻る・再読み込みしても結果が残る */
export function SearchPage() {
  const [params, setParams] = useSearchParams();
  const query = (params.get("q") ?? "").trim();
  const [input, setInput] = useState(query);
  const path = `/api/users?q=${encodeURIComponent(query)}`;
  const paged = usePaged<UserSummary>(path, query ? loadUsers(path, "after") : loadNothing);

  // ブラウザの「戻る」で検索語が変わったら、入力欄も合わせる
  useEffect(() => setInput(query), [query]);

  function submit(event: FormEvent) {
    event.preventDefault();
    const q = input.trim();
    setParams(q ? { q } : {});
  }

  return (
    <>
      <h2 className="screen-title">ユーザー検索</h2>
      <form className="card search-form" onSubmit={submit} role="search">
        <input
          name="q"
          type="search"
          value={input}
          maxLength={QUERY_MAX}
          placeholder="ユーザー名・表示名で探す"
          aria-label="ユーザー名・表示名で探す"
          onChange={(e) => setInput(e.target.value)}
        />
        <button type="submit" className="button primary" disabled={!input.trim()}>
          検索
        </button>
      </form>
      {query ? (
        <UserList paged={paged} emptyMessage={`「${query}」に当てはまるユーザーはいません`} />
      ) : (
        <>
          <p className="empty">ユーザー名や表示名の一部を入れて検索してください</p>
          <Trends />
        </>
      )}
    </>
  );
}

type Trend = { tag: string; postsCount: number };

/** トレンド(F-22): この24時間によく使われたハッシュタグ */
function Trends() {
  const [trends, setTrends] = useState<Trend[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    api<Trend[]>("/api/trends")
      .then((list) => !cancelled && setTrends(list))
      // トレンドは無くても検索はできるので、失敗したら出さない
      .catch(() => !cancelled && setTrends([]));
    return () => {
      cancelled = true;
    };
  }, []);

  if (!trends || trends.length === 0) return null;
  return (
    <section className="card form">
      <h3>トレンド（24時間）</h3>
      <ol className="trend-list">
        {trends.map((trend) => (
          <li key={trend.tag}>
            <Link to={hashtagPath(trend.tag)} className="hashtag">
              #{trend.tag}
            </Link>
            <span className="muted">{trend.postsCount}件</span>
          </li>
        ))}
      </ol>
    </section>
  );
}
