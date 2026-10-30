import type { PublicUser } from "./types";

/** API がエラーを返したとき。message はサーバーの日本語メッセージ */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

// ログイン中に 401 が返ったとき(セッション切れ・他の端末でパスワード変更)に呼ぶ。AuthProvider が登録する
let unauthorizedHandler: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

type Options = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  /** JSON にして送る */
  body?: unknown;
  /** ファイルを送るとき(multipart) */
  form?: FormData;
};

/**
 * API を呼ぶ。Cookie(ログインのセッション)は同じオリジンなので自動で付く。
 * 失敗したら ApiError を投げる
 */
export async function api<T>(path: string, { method = "GET", body, form }: Options = {}): Promise<T> {
  const headers: Record<string, string> = {};
  let payload: BodyInit | undefined;
  if (form) {
    payload = form;
  } else if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }

  let response: Response;
  try {
    response = await fetch(path, { method, headers, body: payload, credentials: "same-origin" });
  } catch {
    throw new ApiError(0, "サーバーに接続できません。時間をおいてもう一度お試しください");
  }

  if (response.status === 204) {
    return undefined as T;
  }
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    const message = data?.message ?? `エラーが発生しました(${response.status})`;
    // ログイン・登録の 401(パスワード違いなど)はセッション切れではないので除く
    if (response.status === 401 && !path.startsWith("/api/auth/")) {
      unauthorizedHandler?.();
    }
    throw new ApiError(response.status, message);
  }
  return data as T;
}

/** エラーを画面に出す文章にする */
export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "予期しないエラーが発生しました";
}

/** アイコン画像の URL。更新時刻を付けて、変えたらブラウザのキャッシュを使わないようにする */
export function iconUrl(user: Pick<PublicUser, "username" | "iconUpdatedAt">): string | null {
  return user.iconUpdatedAt
    ? `/api/users/${encodeURIComponent(user.username)}/icon?v=${Date.parse(user.iconUpdatedAt)}`
    : null;
}
