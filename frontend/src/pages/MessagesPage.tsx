import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useOutletContext, useParams } from "react-router";
import { api, ApiError, errorMessage } from "../api";
import { useMe } from "../auth";
import { Avatar } from "../components/Avatar";
import { Composer } from "../components/Composer";
import { LoadFailed } from "../components/LoadFailed";
import { useFeedback } from "../feedback";
import { DM_MAX, fullTime, relativeTime } from "../text";
import type { Conversation, DirectMessage, Profile } from "../types";
import { NotFoundPage } from "./NotFoundPage";

/** 会話を開いている間、新着と既読を確かめる間隔 */
export const CONVERSATION_POLL_MS = 5_000;

/** Layout が渡す。会話を開いて既読にしたら、メニューの未読の数を聞き直す */
export type MessagesOutlet = { onMessagesRead: () => void };

type MessagePage = { messages: DirectMessage[]; hasMore: boolean; nextCursor: string | null };

/** 古い順に並べた a に、b を足す(同じ ID は b の方で置き換える。既読が付いたときのため) */
export function mergeMessages(a: DirectMessage[], b: DirectMessage[]): DirectMessage[] {
  const byId = new Map(a.map((m) => [m.id, m]));
  for (const m of b) byId.set(m.id, m);
  return [...byId.values()].sort((x, y) => {
    const diff = BigInt(x.id) - BigInt(y.id);
    return diff < 0n ? -1 : diff > 0n ? 1 : 0;
  });
}

/** DM の会話一覧(F-23)。最後のメッセージが新しい会話から */
export function MessagesPage() {
  const [conversations, setConversations] = useState<Conversation[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    api<Conversation[]>("/api/dm")
      .then((list) => !cancelled && setConversations(list))
      .catch((e) => !cancelled && setError(errorMessage(e)));
    return () => {
      cancelled = true;
    };
  }, [attempt]);

  return (
    <>
      <h2 className="screen-title">メッセージ</h2>
      {error ? (
        <LoadFailed message={error} onRetry={() => setAttempt((n) => n + 1)} />
      ) : conversations === null ? (
        <p className="empty">読み込み中…</p>
      ) : conversations.length === 0 ? (
        <div className="empty">
          <p>まだメッセージはありません</p>
          <p className="sub">相手のページの「メッセージ」から送れます</p>
        </div>
      ) : (
        <ul className="post-list">
          {conversations.map((c) => (
            <li key={c.partner.id}>
              <Link
                to={`/messages/${c.partner.username}`}
                className={c.unreadCount > 0 ? "conversation unread" : "conversation"}
              >
                <Avatar user={c.partner} />
                <div className="conversation-body">
                  <p>
                    <span className="name">{c.partner.displayName}</span>
                    <span className="sub"> @{c.partner.username}</span>
                    <span className="sub" title={fullTime(c.lastMessage.createdAt)}>
                      {" · "}
                      {relativeTime(c.lastMessage.createdAt)}
                    </span>
                  </p>
                  <p className="conversation-last">
                    {c.lastMessage.mine && "あなた: "}
                    {c.lastMessage.body}
                  </p>
                </div>
                {c.unreadCount > 0 && (
                  <span className="nav-badge" aria-label={`未読 ${c.unreadCount}件`}>
                    {c.unreadCount > 99 ? "99+" : c.unreadCount}
                  </span>
                )}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

/** 相手との DM(/messages/ユーザー名) */
export function ConversationPage() {
  const username = useParams().username ?? "";
  if (!/^[A-Za-z0-9_]{1,15}$/.test(username)) return <NotFoundPage message="ユーザーが見つかりません" />;
  // 相手が変わったら中身を作り直す
  return <ConversationContent key={username.toLowerCase()} username={username} />;
}

function ConversationContent({ username }: { username: string }) {
  const me = useMe();
  const { showError } = useFeedback();
  const onMessagesRead = useOutletContext<MessagesOutlet | undefined>()?.onMessagesRead;
  const [partner, setPartner] = useState<Profile | null>(null);
  const [messages, setMessages] = useState<DirectMessage[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [notFound, setNotFound] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const bottomRef = useRef<HTMLDivElement>(null);
  // 下まで送るのは、開いたとき・自分が送ったとき・下の方を見ているときに届いたときだけ(古いものを読んでいる邪魔をしない)
  const stickToBottom = useRef(true);
  const isMe = me.username.toLowerCase() === username.toLowerCase();

  const fetchLatest = useCallback(async () => {
    const page = await api<MessagePage>(`/api/dm/${username}`);
    return { items: [...page.messages].reverse(), next: page.nextCursor };
  }, [username]);

  // 相手のプロフィールと最初のページ
  useEffect(() => {
    if (isMe) return;
    let cancelled = false;
    setLoadError(null);
    Promise.all([api<Profile>(`/api/users/${username}`), fetchLatest()])
      .then(([profile, page]) => {
        if (cancelled) return;
        setPartner(profile);
        setMessages(page.items);
        setCursor(page.next);
        setLoaded(true);
        onMessagesRead?.();
      })
      .catch((e) => {
        if (cancelled) return;
        if (e instanceof ApiError && e.status === 404) setNotFound(true);
        else setLoadError(errorMessage(e));
      });
    return () => {
      cancelled = true;
    };
  }, [username, isMe, fetchLatest, onMessagesRead, attempt]);

  // 開いている間は、最初のページを5秒ごとに聞き直す(新着と、相手が読んだかを知るため)。タブが裏なら聞かない
  useEffect(() => {
    if (!loaded) return;
    let cancelled = false;
    let running = false;
    const timer = window.setInterval(async () => {
      if (running || document.visibilityState !== "visible") return;
      running = true;
      try {
        const page = await fetchLatest();
        if (cancelled) return;
        // 相手から新しく届いていたら、サーバーで既読になったので、メニューの未読の数も聞き直す
        const newest = page.items.at(-1);
        if (newest && newest.id !== lastIdRef.current && !newest.mine) onMessagesRead?.();
        setMessages((prev) => mergeMessages(prev, page.items));
      } catch {
        // 次の回にまた確かめる
      } finally {
        running = false;
      }
    }, CONVERSATION_POLL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [loaded, fetchLatest, onMessagesRead]);

  // 下の方を見ているかを覚えておく
  useEffect(() => {
    const onScroll = () => {
      stickToBottom.current = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 120;
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const lastId = messages.at(-1)?.id;
  const lastIdRef = useRef(lastId);
  lastIdRef.current = lastId;
  useEffect(() => {
    if (lastId && stickToBottom.current) bottomRef.current?.scrollIntoView?.({ block: "end" });
  }, [lastId]);

  async function loadOlder() {
    if (!cursor || loadingOlder) return;
    setLoadingOlder(true);
    stickToBottom.current = false;
    try {
      const page = await api<MessagePage>(`/api/dm/${username}?before=${encodeURIComponent(cursor)}`);
      setMessages((prev) => mergeMessages(prev, page.messages));
      setCursor(page.nextCursor);
    } catch (e) {
      showError(errorMessage(e));
    } finally {
      setLoadingOlder(false);
    }
  }

  async function send(text: string) {
    try {
      const sent = await api<DirectMessage>(`/api/dm/${username}`, { method: "POST", body: { body: text } });
      stickToBottom.current = true;
      setMessages((prev) => mergeMessages(prev, [sent]));
    } catch (e) {
      showError(errorMessage(e));
      throw e;
    }
  }

  if (isMe) return <NotFoundPage message="自分にはメッセージを送れません" />;
  if (notFound) return <NotFoundPage message={`ユーザーが見つかりません: @${username}`} />;
  if (loadError) return <LoadFailed message={loadError} onRetry={() => setAttempt((n) => n + 1)} />;
  if (!partner) return <p className="empty">読み込み中…</p>;

  const lastMine = [...messages].reverse().find((m) => m.mine);
  return (
    <>
      <div className="screen-title conversation-title">
        <Link to="/messages" aria-label="メッセージの一覧に戻る">
          ←
        </Link>
        <Link to={`/@${partner.username}`} className="conversation-partner">
          <Avatar user={partner} />
          <span>
            <span className="name">{partner.displayName}</span>
            <span className="sub"> @{partner.username}</span>
          </span>
        </Link>
      </div>
      {cursor && (
        <button type="button" className="button more" disabled={loadingOlder} onClick={loadOlder}>
          {loadingOlder ? "読み込み中…" : "前のメッセージを見る"}
        </button>
      )}
      {messages.length === 0 && <p className="empty">まだメッセージはありません。最初のメッセージを送ってみましょう</p>}
      <ol className="dm-list" aria-label={`${partner.displayName}さんとのメッセージ`}>
        {messages.map((m) => (
          <li key={m.id} className={m.mine ? "dm mine" : "dm"}>
            <p className="dm-bubble">{m.body}</p>
            <span className="sub" title={fullTime(m.createdAt)}>
              {relativeTime(m.createdAt)}
              {m === lastMine && (m.read ? " · 既読" : " · 送信済み")}
            </span>
          </li>
        ))}
      </ol>
      <div ref={bottomRef} />
      <Composer placeholder="メッセージを入力" submitLabel="送信" max={DM_MAX} onSubmit={(text) => send(text)} />
    </>
  );
}
