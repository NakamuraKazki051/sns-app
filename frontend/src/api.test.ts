import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiError, setUnauthorizedHandler } from "./api";

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

  it("ログインの 401(パスワード違い)では作り直さない", async () => {
    const fetchMock = mockFetch(() => ({ status: 401, body: { message: "メールアドレスまたはパスワードが正しくありません" } }));

    await expect(api("/api/auth/login", { method: "POST", body: {} })).rejects.toBeInstanceOf(ApiError);
    expect(calls(fetchMock, "/api/auth/refresh")).toBe(0);
  });
});
