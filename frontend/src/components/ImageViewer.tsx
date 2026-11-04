import { useEffect, useRef } from "react";

type Props = {
  src: string;
  alt: string;
  onClose: () => void;
};

/** 投稿の画像を画面いっぱいに見る。Esc・背景・×で閉じる */
export function ImageViewer({ src, alt, onClose }: Props) {
  const closeRef = useRef<HTMLButtonElement>(null);

  // 開くと×へ移り、閉じたら開いたボタンへ戻す(キーボードで操作している人が場所を見失わないように)
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    closeRef.current?.focus();
    return () => opener?.focus();
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    // 開いている間は後ろの一覧がスクロールしないようにする
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = overflow;
    };
  }, [onClose]);

  return (
    <div className="image-viewer" role="dialog" aria-modal="true" aria-label="画像" onClick={onClose}>
      <img src={src} alt={alt} onClick={(e) => e.stopPropagation()} />
      <button type="button" className="image-viewer-close" ref={closeRef} aria-label="閉じる" onClick={onClose}>
        ×
      </button>
    </div>
  );
}
