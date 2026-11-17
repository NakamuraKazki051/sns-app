import { useEffect, useState } from "react";
import { Link } from "react-router";
import { api, errorMessage } from "../api";
import { useAuth, useMe } from "../auth";
import { useFeedback } from "../feedback";
import type { Me, UserSummary } from "../types";

/**
 * 設定画面の「鍵アカウント」(F-26)。鍵を掛けると、投稿は承認したフォロワーにだけ見え、
 * フォローにはリクエストの承認が要る。届いたリクエストをここで承認・拒否する
 */
export function PrivacySettings() {
  const me = useMe();
  const { updateMe } = useAuth();
  const { showError, showToast } = useFeedback();
  const [requests, setRequests] = useState<UserSummary[] | null>(null);
  const [busy, setBusy] = useState(false);
  const isPrivate = me.privateAccount ?? false;

  useEffect(() => {
    let cancelled = false;
    api<UserSummary[]>("/api/me/follow-requests")
      .then((list) => !cancelled && setRequests(list ?? []))
      .catch(() => !cancelled && setRequests([]));
    return () => {
      cancelled = true;
    };
  }, [isPrivate]);

  // 通知の「設定で承認・拒否する」から来たら、この欄まで送る
  useEffect(() => {
    if (window.location.hash === "#follow-requests" && requests !== null) {
      document.getElementById("follow-requests")?.scrollIntoView?.();
    }
  }, [requests]);

  async function toggle() {
    setBusy(true);
    try {
      updateMe(await api<Me>("/api/me/profile", { method: "PATCH", body: { privateAccount: !isPrivate } }));
      showToast(isPrivate ? "鍵を外しました。届いていたリクエストはすべて承認しました" : "鍵アカウントにしました");
    } catch (e) {
      showError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function answer(user: UserSummary, approve: boolean) {
    setBusy(true);
    try {
      await api<void>(`/api/me/follow-requests/${user.username}`, { method: approve ? "POST" : "DELETE" });
      setRequests((list) => (list ?? []).filter((u) => u.id !== user.id));
      showToast(approve ? `@${user.username} さんを承認しました` : `@${user.username} さんのリクエストを拒否しました`);
    } catch (e) {
      showError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card form" id="follow-requests">
      <h3>鍵アカウント</h3>
      <label className="checkbox">
        <input type="checkbox" checked={isPrivate} disabled={busy} onChange={toggle} />
        投稿を、承認したフォロワーだけに見せる
      </label>
      <p className="muted">
        鍵を掛けると、新しくフォローするにはあなたの承認が要ります。今のフォロワーはそのままです。鍵アカウントの投稿はリポストできません。
      </p>
      {requests !== null && (isPrivate || requests.length > 0) && (
        <>
          <h4>フォローのリクエスト</h4>
          {requests.length === 0 ? (
            <p className="muted">届いているリクエストはありません</p>
          ) : (
            <ul className="relation-list" aria-label="フォローのリクエスト">
              {requests.map((user) => (
                <li key={user.id}>
                  <Link to={`/@${user.username}`}>
                    {user.displayName} <span className="muted">@{user.username}</span>
                  </Link>
                  <span className="request-buttons">
                    <button type="button" className="button primary" disabled={busy} onClick={() => answer(user, true)}>
                      承認
                    </button>
                    <button type="button" className="button" disabled={busy} onClick={() => answer(user, false)}>
                      拒否
                    </button>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  );
}
