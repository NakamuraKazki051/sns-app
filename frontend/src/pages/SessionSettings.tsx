import { useEffect, useState } from "react";
import { api, errorMessage } from "../api";
import { useAuth } from "../auth";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { useFeedback } from "../feedback";
import { fullTime, relativeTime } from "../text";

export type Session = { id: string; device: string; createdAt: string; lastUsedAt: string; current: boolean };

/**
 * 設定画面の「ログイン中の端末」。どの端末でログインしているかを見て、選んだ端末をログアウトさせる。
 * 心当たりのない端末があれば、ログアウトさせてからパスワードを変える
 */
export function SessionSettings() {
  const { logout } = useAuth();
  const { showError, showToast } = useFeedback();
  const [sessions, setSessions] = useState<Session[] | null>(null);
  const [confirmingOthers, setConfirmingOthers] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api<Session[]>("/api/me/sessions")
      .then((list) => !cancelled && setSessions(list))
      .catch(() => !cancelled && setSessions([]));
    return () => {
      cancelled = true;
    };
  }, []);

  async function revoke(session: Session) {
    if (session.current) {
      // この端末ならふつうのログアウトと同じ
      await logout();
      return;
    }
    setBusy(true);
    try {
      await api<void>(`/api/me/sessions/${encodeURIComponent(session.id)}`, { method: "DELETE" });
      setSessions((list) => (list ?? []).filter((s) => s.id !== session.id));
      showToast(`${session.device}をログアウトさせました`);
    } catch (e) {
      showError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function revokeOthers() {
    setConfirmingOthers(false);
    setBusy(true);
    try {
      await api<void>("/api/me/sessions", { method: "DELETE" });
      setSessions((list) => (list ?? []).filter((s) => s.current));
      showToast("ほかの端末をすべてログアウトさせました");
    } catch (e) {
      showError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  const others = (sessions ?? []).filter((s) => !s.current).length;

  return (
    <section className="card form">
      <h3>ログイン中の端末</h3>
      <p className="muted">心当たりのない端末があれば、ログアウトさせてからパスワードを変えてください。</p>
      {sessions === null ? (
        <p className="muted">読み込み中…</p>
      ) : (
        <ul className="session-list" aria-label="ログイン中の端末">
          {sessions.map((s) => (
            <li key={s.id}>
              <div>
                <strong>{s.device}</strong>
                {s.current && <span className="badge">この端末</span>}
                <div className="muted small">
                  <span title={fullTime(s.createdAt)}>{fullTime(s.createdAt)}にログイン</span>
                  {" · "}
                  <span title={fullTime(s.lastUsedAt)}>
                    {s.current ? "使用中" : `最後に使ったのは${relativeTime(s.lastUsedAt)}`}
                  </span>
                </div>
              </div>
              <button
                type="button"
                className="button"
                disabled={busy}
                aria-label={s.current ? "この端末をログアウト" : `${s.device}をログアウト`}
                onClick={() => revoke(s)}
              >
                ログアウト
              </button>
            </li>
          ))}
        </ul>
      )}
      {others > 0 && (
        <div>
          <button type="button" className="button danger" disabled={busy} onClick={() => setConfirmingOthers(true)}>
            ほかの端末をすべてログアウト
          </button>
        </div>
      )}
      {confirmingOthers && (
        <ConfirmDialog
          title="ほかの端末をすべてログアウトしますか？"
          message={`この端末以外の${others}台がログアウトします。`}
          confirmLabel="ログアウトさせる"
          onConfirm={revokeOthers}
          onCancel={() => setConfirmingOthers(false)}
        />
      )}
    </section>
  );
}
