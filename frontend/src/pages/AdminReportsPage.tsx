import { useEffect, useState } from "react";
import { Link } from "react-router";
import { api, ApiError, errorMessage } from "../api";
import { REPORT_REASONS, type ReportReason } from "../components/ReportDialog";
import { useFeedback } from "../feedback";
import { fullTime } from "../text";
import type { PublicUser } from "../types";
import { NotFoundPage } from "./NotFoundPage";

type Resolution = "DISMISSED" | "POST_DELETED";

export type AdminReport = {
  id: string;
  reason: ReportReason;
  detail: string | null;
  createdAt: string;
  reporter: PublicUser;
  target: PublicUser;
  post: { id: string | null; body: string } | null;
  resolvedAt: string | null;
  resolution: Resolution | null;
};

const reasonLabel = (reason: ReportReason) => REPORT_REASONS.find((r) => r.value === reason)?.label ?? reason;
const RESOLUTION_LABEL: Record<Resolution, string> = { DISMISSED: "問題なし", POST_DELETED: "投稿を削除" };

/** 管理者だけが開ける「通報の確認」。対応前の通報を古い順に見て、問題なしにするか投稿を消す */
export function AdminReportsPage() {
  const { showError, showToast } = useFeedback();
  const [status, setStatus] = useState<"open" | "resolved">("open");
  const [reports, setReports] = useState<AdminReport[] | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setReports(null);
    api<AdminReport[]>(`/api/admin/reports?status=${status}`)
      .then((list) => !cancelled && setReports(list))
      .catch((e) => {
        if (cancelled) return;
        if (e instanceof ApiError && e.status === 404) setForbidden(true);
        else showError(errorMessage(e));
        setReports([]);
      });
    return () => {
      cancelled = true;
    };
  }, [status, showError]);

  async function resolve(report: AdminReport, resolution: Resolution) {
    setBusy(true);
    try {
      await api<void>(`/api/admin/reports/${report.id}/resolve`, { method: "POST", body: { resolution } });
      // 投稿を消したら、同じ投稿への通報もまとめて片付く
      setReports((list) =>
        (list ?? []).filter(
          (r) => r.id !== report.id && !(resolution === "POST_DELETED" && r.post?.id != null && r.post.id === report.post?.id),
        ),
      );
      showToast(resolution === "POST_DELETED" ? "投稿を削除しました" : "対応済みにしました");
    } catch (e) {
      showError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  if (forbidden) return <NotFoundPage />;

  return (
    <>
      <h2 className="screen-title">通報の確認</h2>
      <div className="tabs" role="tablist">
        <button type="button" role="tab" aria-selected={status === "open"} className={status === "open" ? "active" : undefined} onClick={() => setStatus("open")}>
          対応前
        </button>
        <button type="button" role="tab" aria-selected={status === "resolved"} className={status === "resolved" ? "active" : undefined} onClick={() => setStatus("resolved")}>
          対応済み
        </button>
      </div>
      <section className="card">
        {reports === null ? (
          <p className="muted">読み込み中…</p>
        ) : reports.length === 0 ? (
          <p className="muted">{status === "open" ? "対応前の通報はありません" : "対応済みの通報はありません"}</p>
        ) : (
          <ul className="relation-list" aria-label="通報">
            {reports.map((r) => (
              <li key={r.id} className="admin-report" style={{ display: "block" }}>
                <div>
                  <strong>{reasonLabel(r.reason)}</strong>
                  <span className="muted"> · {fullTime(r.createdAt)}</span>
                  {r.resolution && <span className="badge">{RESOLUTION_LABEL[r.resolution]}</span>}
                </div>
                <div className="muted">
                  <Link to={`/@${r.reporter.username}`}>@{r.reporter.username}</Link> さんが
                  <Link to={`/@${r.target.username}`}>@{r.target.username}</Link> さんの{r.post ? "投稿" : "アカウント"}を通報
                </div>
                {r.post && (
                  <blockquote>
                    {r.post.id ? <Link to={`/posts/${r.post.id}`}>{r.post.body || "(画像だけの投稿)"}</Link> : r.post.body}
                    {!r.post.id && <span className="muted">(削除済み)</span>}
                  </blockquote>
                )}
                {r.detail && <p>{r.detail}</p>}
                {!r.resolvedAt && (
                  <div className="modal-actions">
                    <button type="button" className="button" disabled={busy} onClick={() => resolve(r, "DISMISSED")}>
                      問題なし
                    </button>
                    {r.post?.id && (
                      <button type="button" className="button danger" disabled={busy} onClick={() => resolve(r, "POST_DELETED")}>
                        投稿を削除
                      </button>
                    )}
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
