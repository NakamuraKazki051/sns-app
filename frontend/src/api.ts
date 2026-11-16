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

/**
 * 作り直しの結果。ok: できた / unauthorized: リフレッシュトークンが使えない(ログイン切れ) /
 * error: サーバーの不調・通信の失敗(ログイン切れとは限らないので、ログアウト扱いにしない)
 */
export type RefreshResult = "ok" | "unauthorized" | "error";

// 作り直しの最中なら、その結果を待つ(同時に 401 になった API が、それぞれ作り直さないように)
let refreshing: Promise<RefreshResult> | null = null;

/** サーバーが応答に付ける、アクセストークンの残り秒数(CurrentUser.EXPIRES_IN_HEADER) */
const EXPIRES_IN_HEADER = "X-Access-Token-Expires-In";
let refreshTimer: ReturnType<typeof setTimeout> | undefined;
/**
 * アクセストークンが切れる時刻(この端末の時計で)。隠れたタブは Chrome がタイマーごと止めるので、
 * 先回りの作り直しが間に合わないことがある。再開して API を送るときに過ぎていれば、先に作り直す
 */
let accessExpiresAt: number | undefined;
const SAME_TOKEN_TOLERANCE_MS = 5_000;

/**
 * アクセストークンが切れる少し前に、先回りして作り直す。切れてから 401 を受けて作り直すと、
 * そのたびにブラウザのコンソールに 401 のエラーが出るため。残りの2割(最大60秒)を残して作り直す
 */
function scheduleRefresh(response: Response) {
  const header = response.headers.get(EXPIRES_IN_HEADER);
  const seconds = Number(header);
  if (header === null || !Number.isFinite(seconds) || seconds <= 0) return;
  const expiresAt = Date.now() + seconds * 1000;
  // 同じトークンの応答なら、予定を立て直さない。残りが減るたびに立て直すと「残りの2割」も縮み、
  // 作り直しが期限の直前までずれ込む(AWS で、900秒のうち893秒後になっていた)。
  // 秒の切り捨てと通信の時間で数秒ずれるので、5秒以内の差は同じトークンとみなす
  if (accessExpiresAt !== undefined && Math.abs(expiresAt - accessExpiresAt) < SAME_TOKEN_TOLERANCE_MS) return;
  accessExpiresAt = expiresAt;
  const delay = seconds - Math.min(60, seconds * 0.2);
  clearTimeout(refreshTimer);
  refreshTimer = setTimeout(() => void refreshTokens(), delay * 1000);
}

/** ログアウトしたら、先回りの作り直しをやめる */
export function cancelScheduledRefresh() {
  clearTimeout(refreshTimer);
  refreshTimer = undefined;
  accessExpiresAt = undefined;
}

/** リフレッシュトークン(Cookie SNS_REFRESH)で、アクセストークン(SNS_TOKEN)を作り直す */
export function refreshTokens(): Promise<RefreshResult> {
  refreshing ??= fetch("/api/auth/refresh", { method: "POST", credentials: "same-origin" })
    .then((response): RefreshResult => {
      if (response.ok) {
        scheduleRefresh(response);
        return "ok";
      }
      return response.status === 401 ? "unauthorized" : "error";
    })
    .catch((): RefreshResult => "error")
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

const OFFLINE = "サーバーに接続できません。時間をおいてもう一度お試しください";

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
  let refreshResult: RefreshResult | undefined;
  try {
    // 切れているのが分かっていれば、401 を受ける前に作り直しておく(結果は下の 401 の処理で見る)
    let early: RefreshResult | undefined;
    if (accessExpiresAt !== undefined && Date.now() >= accessExpiresAt && canRefresh(path)) {
      early = await refreshTokens();
    }
    response = await send();
    if (response.status === 401 && canRefresh(path)) {
      // 先に作り直せなかったなら、同じことを繰り返さない
      refreshResult = early !== undefined && early !== "ok" ? early : await refreshTokens();
      if (refreshResult === "ok") {
        response = await send();
      }
    }
  } catch {
    throw new ApiError(0, OFFLINE);
  }
  if (refreshResult === "error") {
    // 作り直しがサーバーの不調で失敗しただけなら、ログイン切れにはしない(しばらくしてやり直せばよい)
    throw new ApiError(0, OFFLINE);
  }
  scheduleRefresh(response);

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
