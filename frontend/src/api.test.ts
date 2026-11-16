import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiError, cancelScheduledRefresh, setUnauthorizedHandler } from "./api";

type Handler = (url: string) => { status: number; body?: unknown };

function mockFetch(handler: Handler) {
  const fetchMock = vi.fn(async (url: string) => {
    const { status, body } = handler(url);
    return new Response(body === undefined ? null : JSON.stringify(body), { status });
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

const calls = (fetchMock: ReturnType<typeof mockFetch>, url: string) =>
  fetchMock.mock.calls.filter(([called]) => called === url).length;

afterEach(() => {
  vi.unstubAllGlobals();
  setUnauthorizedHandler(null);
});

describe("api: アクセストークンの作り直し", () => {
  it("401 になったらリフレッシュトークンで作り直し、同じ API をやり直す", async () => {
    let refreshed = false;
    const fetchMock = mockFetch((url) => {
      if (url === "/api/auth/refresh") {
        refreshed = true;
        return { status: 204 };
      }
      return refreshed ? { status: 200, body: { ok: true } } : { status: 401, body: { message: "期限切れ" } };
    });

    await expect(api("/api/timeline")).resolves.toEqual({ ok: true });
    expect(calls(fetchMock, "/api/timeline")).toBe(2);
    expect(calls(fetchMock, "/api/auth/refresh")).toBe(1);
  });

  it("同時に 401 になっても、作り直しは1回だけ", async () => {
    let refreshed = false;
    const fetchMock = mockFetch((url) => {
      if (url === "/api/auth/refresh") {
        refreshed = true;
        return { status: 204 };
      }
      return refreshed ? { status: 200, body: [] } : { status: 401, body: {} };
    });

    await Promise.all([api("/api/timeline"), api("/api/notifications"), api("/api/auth/me")]);
    expect(calls(fetchMock, "/api/auth/refresh")).toBe(1);
  });

  it("作り直せなければ、ログイン切れとして扱う", async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    const fetchMock = mockFetch(() => ({ status: 401, body: { message: "ログインの有効期限が切れました" } }));

    await expect(api("/api/timeline")).rejects.toEqual(new ApiError(401, "ログインの有効期限が切れました"));
    expect(calls(fetchMock, "/api/timeline")).toBe(1);
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it("作り直しがサーバーの不調で失敗したときは、ログイン切れにしない", async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    mockFetch((url) => (url === "/api/auth/refresh" ? { status: 500, body: {} } : { status: 401, body: {} }));

    await expect(api("/api/timeline")).rejects.toMatchObject({ status: 0 });
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it("アクセストークンが切れる少し前に、先回りして作り直す", async () => {
    vi.useFakeTimers();
    try {
      const fetchMock = vi.fn(async (url: string) =>
        url === "/api/auth/refresh"
          ? new Response(null, { status: 204, headers: { "X-Access-Token-Expires-In": "900" } })
          : new Response("[]", { status: 200, headers: { "X-Access-Token-Expires-In": "100" } }),
      );
      vi.stubGlobal("fetch", fetchMock);

      await api("/api/timeline");
      // 残り100秒なら、2割(20秒)を残して80秒後に作り直す
      await vi.advanceTimersByTimeAsync(79_000);
      expect(calls(fetchMock as never, "/api/auth/refresh")).toBe(0);
      await vi.advanceTimersByTimeAsync(1_000);
      expect(calls(fetchMock as never, "/api/auth/refresh")).toBe(1);
      // 作り直したら、次は残り900秒から60秒を残して840秒後
      await vi.advanceTimersByTimeAsync(839_000);
      expect(calls(fetchMock as never, "/api/auth/refresh")).toBe(1);
      await vi.advanceTimersByTimeAsync(1_000);
      expect(calls(fetchMock as never, "/api/auth/refresh")).toBe(2);

      // ログアウトしたら、もう作り直さない
      cancelScheduledRefresh();
      await vi.advanceTimersByTimeAsync(2_000_000);
      expect(calls(fetchMock as never, "/api/auth/refresh")).toBe(2);
    } finally {
      cancelScheduledRefresh();
      vi.useRealTimers();
    }
  });

  it("30秒ごとの新着チェックがあっても、作り直しの予定は後ろにずれない", async () => {
    vi.useFakeTimers();
    try {
      // 本物のサーバーと同じく、残り秒数は時間とともに減る
      let expiresAt = Date.now() + 900_000;
      const fetchMock = vi.fn(async (url: string) => {
        if (url === "/api/auth/refresh") expiresAt = Date.now() + 900_000;
        const left = String(Math.floor((expiresAt - Date.now()) / 1000));
        return url === "/api/auth/refresh"
          ? new Response(null, { status: 204, headers: { "X-Access-Token-Expires-In": left } })
          : new Response("[]", { status: 200, headers: { "X-Access-Token-Expires-In": left } });
      });
      vi.stubGlobal("fetch", fetchMock);

      await api("/api/timeline");
      // 30秒ごとに API を呼びながら、14分まで進める
      for (let t = 30; t < 840; t += 30) {
        await vi.advanceTimersByTimeAsync(30_000);
        await api("/api/notifications/unread-count");
      }
      expect(calls(fetchMock as never, "/api/auth/refresh")).toBe(0);
      // 期限の60秒前(840秒)に作り直す
      await vi.advanceTimersByTimeAsync(30_000);
      expect(calls(fetchMock as never, "/api/auth/refresh")).toBe(1);
    } finally {
      cancelScheduledRefresh();
      vi.useRealTimers();
    }
  });

  it("タブが止められて期限を過ぎていたら、API を送る前に作り直す(401 を出さない)", async () => {
    vi.useFakeTimers();
    try {
      const fetchMock = vi.fn(async (url: string) =>
        url === "/api/auth/refresh"
          ? new Response(null, { status: 204, headers: { "X-Access-Token-Expires-In": "900" } })
          : new Response("[]", { status: 200, headers: { "X-Access-Token-Expires-In": "100" } }),
      );
      vi.stubGlobal("fetch", fetchMock);
      await api("/api/timeline");

      // タイマーは動かさずに時計だけ進める(Chrome が隠れたタブを止めたときと同じ)
      vi.setSystemTime(Date.now() + 200_000);
      await api("/api/notifications");
      const order = fetchMock.mock.calls.map(([url]) => url);
      expect(order).toEqual(["/api/timeline", "/api/auth/refresh", "/api/notifications"]);
    } finally {
      cancelScheduledRefresh();
      vi.useRealTimers();
    }
  });

  it("ログインの 401(パスワード違い)では作り直さない", async () => {
    const fetchMock = mockFetch(() => ({ status: 401, body: { message: "メールアドレスまたはパスワードが正しくありません" } }));

    await expect(api("/api/auth/login", { method: "POST", body: {} })).rejects.toBeInstanceOf(ApiError);
    expect(calls(fetchMock, "/api/auth/refresh")).toBe(0);
  });
});
