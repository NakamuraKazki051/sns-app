import { useState, type ImgHTMLAttributes } from "react";
import { refreshTokens } from "../api";

type Props = ImgHTMLAttributes<HTMLImageElement> & { src: string };

/**
 * ログインが要る画像(投稿の画像・アイコン)。<img> は api() を通らないので、アクセストークンが切れていると
 * 読み込みに失敗する。そのときは1回だけ作り直して、URL に印を付けて読み直す(同じ URL だと読み直さないため)
 */
export function AuthImage({ src, onError, ...rest }: Props) {
  const [retried, setRetried] = useState<string | null>(null);
  const current = retried === src ? `${src}${src.includes("?") ? "&" : "?"}retry=1` : src;
  return (
    <img
      {...rest}
      src={current}
      onError={(event) => {
        if (retried !== src) {
          void refreshTokens().then((result) => {
            if (result === "ok") setRetried(src);
          });
        }
        onError?.(event);
      }}
    />
  );
}
