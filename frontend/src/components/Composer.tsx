import { useState, type FormEvent, type KeyboardEvent } from "react";
import { countChars, POST_MAX } from "../text";

type Props = {
  placeholder: string;
  submitLabel: string;
  /** 送信する。失敗したら例外を投げる(入力は残す) */
  onSubmit: (text: string) => Promise<void>;
};

/** 投稿欄・リプライ欄。残り文字数を出し、140字を超えたり空だったりすると送れない */
export function Composer({ placeholder, submitLabel, onSubmit }: Props) {
  const [text, setText] = useState("");
  const [sending, setSending] = useState(false);
  const remaining = POST_MAX - countChars(text.trim());
  const canSubmit = text.trim() !== "" && remaining >= 0 && !sending;

  async function submit(event?: FormEvent) {
    event?.preventDefault();
    if (!canSubmit) return;
    setSending(true);
    try {
      await onSubmit(text);
      setText("");
    } catch {
      // エラーは呼び出し元がバナーに出す。入力は書き直せるよう残す
    } finally {
      setSending(false);
    }
  }

  // Ctrl+Enter(Mac は ⌘+Enter)でも送れる
  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
      void submit();
    }
  }

  const counterClass = remaining < 0 ? "counter over" : remaining <= 20 ? "counter warn" : "counter";
  return (
    <form className="card composer" onSubmit={submit}>
      <textarea
        rows={3}
        value={text}
        placeholder={placeholder}
        aria-label={placeholder}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={onKeyDown}
      />
      <div className="composer-footer">
        <span className={counterClass} aria-live="polite">
          {remaining}
        </span>
        <button type="submit" className="button primary" disabled={!canSubmit}>
          {sending ? "送信中…" : submitLabel}
        </button>
      </div>
    </form>
  );
}
