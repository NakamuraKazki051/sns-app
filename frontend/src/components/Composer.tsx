import { useEffect, useRef, useState, type ClipboardEvent, type FormEvent, type KeyboardEvent } from "react";
import { ImageError, prepareImage } from "../image";
import { countChars, POST_MAX } from "../text";

type Props = {
  placeholder: string;
  submitLabel: string;
  /** 画像を1枚付けられるようにする(投稿欄だけ。リプライには付けない) */
  allowImage?: boolean;
  /** 送信する。失敗したら例外を投げる(入力は残す) */
  onSubmit: (text: string, image: Blob | null) => Promise<void>;
};

/**
 * 投稿欄・リプライ欄。残り文字数を出し、140字を超えたり空だったりすると送れない。
 * allowImage なら画像を1枚付けられる(画像があれば本文は空でもよい)
 */
export function Composer({ placeholder, submitLabel, allowImage = false, onSubmit }: Props) {
  const [text, setText] = useState("");
  const [image, setImage] = useState<Blob | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [preparing, setPreparing] = useState(false);
  const [imageError, setImageError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const remaining = POST_MAX - countChars(text.trim());
  const hasContent = text.trim() !== "" || image !== null;
  const canSubmit = hasContent && remaining >= 0 && !sending && !preparing;

  // 選んだ画像の見本。外したり送ったりしたら、ブラウザが持っている画像を手放す
  useEffect(() => {
    if (!image) {
      setPreview(null);
      return;
    }
    const url = URL.createObjectURL(image);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [image]);

  async function attach(file: File) {
    setImageError(null);
    setPreparing(true);
    try {
      setImage(await prepareImage(file));
    } catch (e) {
      setImageError(e instanceof ImageError ? e.message : "画像を読み込めませんでした");
    } finally {
      setPreparing(false);
      // 同じファイルを選び直しても選んだことになるよう、選択を空にしておく
      if (fileInput.current) fileInput.current.value = "";
    }
  }

  function removeImage() {
    setImage(null);
    setImageError(null);
  }

  async function submit(event?: FormEvent) {
    event?.preventDefault();
    if (!canSubmit) return;
    setSending(true);
    try {
      await onSubmit(text, image);
      setText("");
      removeImage();
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

  // スクリーンショットなどを貼り付けたら、画像として付ける
  function onPaste(event: ClipboardEvent<HTMLTextAreaElement>) {
    if (!allowImage || image || preparing || sending) return;
    const file = Array.from(event.clipboardData.files).find((f) => f.type.startsWith("image/"));
    if (file) {
      event.preventDefault();
      void attach(file);
    }
  }

  const attachDisabled = image !== null || preparing || sending;
  const counterClass = remaining < 0 ? "counter over" : remaining <= 20 ? "counter warn" : "counter";
  return (
    <form className="card composer" onSubmit={submit}>
      <textarea
        ref={textareaRef}
        rows={3}
        value={text}
        placeholder={placeholder}
        aria-label={placeholder}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={onKeyDown}
        onPaste={onPaste}
      />
      {preview && (
        <div className="composer-image">
          <img src={preview} alt="添付する画像" />
          <button
            type="button"
            className="composer-image-remove"
            aria-label="画像を外す"
            disabled={sending}
            onClick={() => {
              removeImage();
              // ×のボタンが消えるので、続けて操作できるよう入力欄へ移す
              textareaRef.current?.focus();
            }}
          >
            ×
          </button>
        </div>
      )}
      {imageError && (
        <p className="composer-error" role="alert">
          {imageError}
        </p>
      )}
      <div className="composer-footer">
        {allowImage && (
          <label className={attachDisabled ? "composer-attach disabled" : "composer-attach"}>
            <input
              ref={fileInput}
              type="file"
              accept="image/jpeg,image/png"
              aria-label="画像を付ける"
              disabled={attachDisabled}
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) void attach(file);
              }}
            />
            {preparing ? "画像を準備中…" : "🖼 画像"}
          </label>
        )}
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
