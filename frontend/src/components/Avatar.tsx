import { iconUrl } from "../api";
import type { PublicUser } from "../types";

const COLORS = ["#1d9bf0", "#f91880", "#00ba7c", "#7856ff", "#ff7a00", "#e0245e"];

// ユーザー名から毎回同じ色を選ぶ
function colorOf(username: string): string {
  let hash = 0;
  for (const ch of username.toLowerCase()) {
    hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  }
  return COLORS[hash % COLORS.length];
}

type Props = {
  user: Pick<PublicUser, "username" | "displayName" | "iconUpdatedAt">;
  large?: boolean;
  /** 設定画面で、保存前に選んだ画像を見せるとき */
  previewSrc?: string | null;
};

/** アイコン画像。無ければ表示名の頭文字の丸 */
export function Avatar({ user, large = false, previewSrc }: Props) {
  const src = previewSrc !== undefined ? previewSrc : iconUrl(user);
  return (
    <div className={large ? "avatar large" : "avatar"} style={{ background: colorOf(user.username) }} aria-hidden="true">
      {src ? <img src={src} alt="" /> : ([...user.displayName][0] ?? "?")}
    </div>
  );
}
