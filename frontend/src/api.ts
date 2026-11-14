import type { PublicUser } from "./types";

/** API がエラーを返したとき。message はサーバーの日本語メッセージ */
export class ApiError extends Error {
  readonly status: number;
  /** 画面が出し分けに使う目印(ログインで認証アプリのコードが要るときの TWO_FACTOR_REQUIRED など)。普段は undefined */
  readonly code?: string;

  constructor(status: number, message: string, code?: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

// ログイン中に 401 が返り、作り直しもできなかったとき(7日使わなかった・他の端末でパスワード変更)に呼ぶ。AuthProvider が登録する
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

// 作り直しの最中なら、その結果を待つ(同時に 401 になった API が、それぞれ作り直さないように)
let refreshing: Promise<boolean> | null = null;

/** リフレッシュトークン(Cookie SNS_REFRESH)で、アクセストークン(SNS_TOKEN)を作り直す。できたら true */
function refreshTokens(): Promise<boolean> {
  refreshing ??= fetch("/api/auth/refresh", { method: "POST", credentials: "same-origin" })
    .then((response) => response.ok)
    .catch(() => false)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

/** 401 のとき作り直してやり直す API か。ログイン・登録などの 401 はパスワード違いなどなので除く */
function canRefresh(path: string): boolean {
  return !path.startsWith("/api/auth/") || path === "/api/auth/me";
}

/**
 * API を呼ぶ。Cookie(ログインのトークン)は同じオリジンなので自動で付く。
 * アクセストークン(15分)が切れて 401 になったら、リフレッシュトークンで作り直して1回だけやり直す。
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

  const send = () => fetch(path, { method, headers, body: payload, credentials: "same-origin" });
  let response: Response;
  try {
    response = await send();
    if (response.status === 401 && canRefresh(path) && (await refreshTokens())) {
      response = await send();
    }
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
    throw new ApiError(response.status, message, typeof data?.code === "string" ? data.code : undefined);
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
