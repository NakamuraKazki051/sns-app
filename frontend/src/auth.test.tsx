import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "./api";
import { AuthProvider, RequireAuth, useAuth } from "./auth";
import { LoginPage } from "./pages/LoginPage";

const ME = {
  id: "u1",
  email: "alice@example.com",
  username: "alice",
  displayName: "アリス",
  iconUpdatedAt: null,
  bio: null,
  createdAt: "2026-10-30T00:00:00Z",
};

type Handler = (url: string, init: RequestInit) => { status: number; body?: unknown };

function mockFetch(handler: Handler) {
  const fetchMock = vi.fn(async (url: string, init: RequestInit = {}) => {
    const { status, body } = handler(url, init);
    return new Response(body === undefined ? null : JSON.stringify(body), { status });
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function WhereAmI() {
  const location = useLocation();
  return <p data-testid="location">{location.pathname + location.search}</p>;
}

function Secret() {
  const { me } = useAuth();
  return <p>ようこそ {me?.displayName}</p>;
}

function renderApp(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="*"
            element={
              <RequireAuth>
                <Secret />
              </RequireAuth>
            }
          />
        </Routes>
        <WhereAmI />
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => vi.unstubAllGlobals());
afterEach(() => vi.unstubAllGlobals());

describe("ログインが必要な画面", () => {
  it("未ログインならログイン画面へ移し、戻り先を覚えておく", async () => {
    mockFetch(() => ({ status: 401, body: { message: "ログインが必要です" } }));
    renderApp("/@bob?x=1");
    await waitFor(() =>
      expect(screen.getByTestId("location").textContent).toBe(`/login?next=${encodeURIComponent("/@bob?x=1")}`),
    );
  });

  it("ログイン済みなら、そのまま見られる", async () => {
    mockFetch(() => ({ status: 200, body: ME }));
    renderApp("/");
    expect(await screen.findByText("ようこそ アリス")).toBeTruthy();
  });

  it("ログインすると、覚えておいた画面に戻る", async () => {
    let loggedIn = false;
    const fetchMock = mockFetch((url) => {
      if (url === "/api/auth/login") {
        loggedIn = true;
        return { status: 200, body: ME };
      }
      return loggedIn ? { status: 200, body: ME } : { status: 401, body: { message: "ログインが必要です" } };
    });
    renderApp("/@bob");
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText("メールアドレス"), "alice@example.com");
    await user.type(screen.getByLabelText(/^パスワード/), "password123");
    await user.click(screen.getByRole("button", { name: "ログイン" }));

    await waitFor(() => expect(screen.getByTestId("location").textContent).toBe("/@bob"));
    const loginCall = fetchMock.mock.calls.find(([url]) => url === "/api/auth/login")!;
    expect(JSON.parse(loginCall[1]?.body as string)).toEqual({ email: "alice@example.com", password: "password123" });
  });

  it("パスワード違いはログイン画面にエラーを出し、セッション切れ扱いにはしない", async () => {
    mockFetch((url) =>
      url === "/api/auth/login"
        ? { status: 401, body: { message: "メールアドレスまたはパスワードが正しくありません" } }
        : { status: 401, body: { message: "ログインが必要です" } },
    );
    renderApp("/login");
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText("メールアドレス"), "alice@example.com");
    await user.type(screen.getByLabelText(/^パスワード/), "wrong-password");
    await user.click(screen.getByRole("button", { name: "ログイン" }));

    expect((await screen.findByRole("alert")).textContent).toBe("メールアドレスまたはパスワードが正しくありません");
    expect(screen.queryByText(/有効期限が切れました/)).toBeNull();
  });

  it("新規登録は画面で形式を確かめ、通れば登録してそのままログインする", async () => {
    const fetchMock = mockFetch((url) => {
      if (url === "/api/auth/register") return { status: 201, body: ME };
      if (url === "/api/auth/login") return { status: 200, body: ME };
      return { status: 401, body: { message: "ログインが必要です" } };
    });
    renderApp("/login");
    const user = userEvent.setup();
    await user.click(await screen.findByRole("tab", { name: "新規登録" }));
    await user.type(screen.getByLabelText(/^ユーザー名/), "bad-name");
    await user.type(screen.getByLabelText("表示名"), "アリス");
    await user.type(screen.getByLabelText("メールアドレス"), "alice@example.com");
    await user.type(screen.getByLabelText(/^パスワード/), "password123");
    await user.click(screen.getByRole("button", { name: "登録してはじめる" }));
    expect((await screen.findByRole("alert")).textContent).toContain("ユーザー名は半角英数字");
    expect(fetchMock.mock.calls.some(([url]) => url === "/api/auth/register")).toBe(false);

    await user.clear(screen.getByLabelText(/^ユーザー名/));
    await user.type(screen.getByLabelText(/^ユーザー名/), "alice");
    await user.click(screen.getByRole("button", { name: "登録してはじめる" }));
    await waitFor(() => expect(screen.getByTestId("location").textContent).toBe("/"));
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(
      expect.arrayContaining(["/api/auth/register", "/api/auth/login"]),
    );
  });

  it("自分でログアウトしたら、戻り先を付けずにログイン画面へ移す", async () => {
    let loggedOut = false;
    mockFetch((url) => {
      if (url === "/api/auth/logout") {
        loggedOut = true;
        return { status: 204 };
      }
      return loggedOut ? { status: 401, body: {} } : { status: 200, body: ME };
    });
    function LogoutButton() {
      const { logout } = useAuth();
      return (
        <button type="button" onClick={() => logout()}>
          ログアウト
        </button>
      );
    }
    render(
      <MemoryRouter initialEntries={["/posts/5"]}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="*"
              element={
                <RequireAuth>
                  <LogoutButton />
                </RequireAuth>
              }
            />
          </Routes>
          <WhereAmI />
        </AuthProvider>
      </MemoryRouter>,
    );
    await userEvent.setup().click(await screen.findByRole("button", { name: "ログアウト" }));
    await waitFor(() => expect(screen.getByTestId("location").textContent).toBe("/login"));
    expect(screen.queryByText(/有効期限が切れました/)).toBeNull();
  });

  it("ログイン中に API が 401 を返したら、ログイン画面で理由を伝える", async () => {
    let expired = false;
    mockFetch((url) => {
      if (url === "/api/auth/me") return expired ? { status: 401, body: {} } : { status: 200, body: ME };
      return { status: 401, body: { message: "ログインの有効期限が切れました" } };
    });
    renderApp("/");
    await screen.findByText("ようこそ アリス");

    expired = true;
    await act(async () => {
      await api("/api/timeline").catch(() => undefined);
    });
    await waitFor(() => expect(screen.getByTestId("location").textContent).toBe(`/login?next=${encodeURIComponent("/")}`));
    expect(screen.getByText("ログインの有効期限が切れました。もう一度ログインしてください")).toBeTruthy();
  });
});
