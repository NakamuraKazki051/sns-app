import { useEffect, useState, type FormEvent } from "react";
import { api, errorMessage } from "../api";
import { useFeedback } from "../feedback";
import { countChars } from "../text";

export type ReportReason = "SPAM" | "HARASSMENT" | "INAPPROPRIATE" | "IMPERSONATION" | "OTHER";

export const REPORT_REASONS: { value: ReportReason; label: string }[] = [
  { value: "SPAM", label: "スパム・宣伝" },
  { value: "HARASSMENT", label: "嫌がらせ・攻撃的な内容" },
  { value: "INAPPROPRIATE", label: "不適切な画像・内容" },
  { value: "IMPERSONATION", label: "なりすまし" },
  { value: "OTHER", label: "その他" },
];

const DETAIL_MAX = 500;

type Props = {
  /** 投稿の通報なら投稿 ID、ユーザーの通報ならユーザー名 */
  target: { postId: string } | { username: string };
  onClose: () => void;
};

/** 通報の画面。理由を選び、必要なら説明を書いて送る。相手には知らせない */
export function ReportDialog({ target, onClose }: Props) {
  const { showError, showToast } = useFeedback();
  const [reason, setReason] = useState<ReportReason | null>(null);
  const [detail, setDetail] = useState("");
  const [sending, setSending] = useState(false);
  const isPost = "postId" in target;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!reason) {
      showError("通報の理由を選んでください");
      return;
    }
    if (countChars(detail.trim()) > DETAIL_MAX) {
      showError(`詳しい説明は${DETAIL_MAX}文字以内で入力してください`);
      return;
    }
    setSending(true);
    try {
      await api<void>("/api/reports", { method: "POST", body: { ...target, reason, detail: detail.trim() || undefined } });
      showToast("通報しました。ご協力ありがとうございます");
      onClose();
    } catch (e) {
      showError(errorMessage(e));
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <form
        className="modal report-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="report-title"
        onClick={(e) => e.stopPropagation()}
        onSubmit={submit}
      >
        <h3 id="report-title">{isPost ? "投稿を通報" : "ユーザーを通報"}</h3>
        <p className="muted">運営が確認します。相手には知らせません。</p>
        <fieldset>
          <legend>理由</legend>
          {REPORT_REASONS.map((r) => (
            <label key={r.value} className="radio">
              <input
                type="radio"
                name="reason"
                value={r.value}
                checked={reason === r.value}
                onChange={() => setReason(r.value)}
              />
              {r.label}
            </label>
          ))}
        </fieldset>
        <label>
          詳しい説明（任意）
          <textarea value={detail} onChange={(e) => setDetail(e.target.value)} rows={3} />
        </label>
        <div className="modal-actions">
          <button type="button" className="button" onClick={onClose}>
            キャンセル
          </button>
          <button type="submit" className="button danger" disabled={sending}>
            通報する
          </button>
        </div>
      </form>
    </div>
  );
}
