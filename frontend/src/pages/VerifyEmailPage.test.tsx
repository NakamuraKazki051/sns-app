import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../auth";
import { VerifyEmailPage } from "./VerifyEmailPage";

function mockFetch(verify: { status: number; body?: unknown }) {
  const fetchMock = vi.fn(async (url: string) => {
    const { status, body } = url === "/api/auth/verify-email" ? verify : { status: 401, body: { message: "ログインしていません" } };
    return new Response(body === undefined ? null : JSON.stringify(body), { status });
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function renderPage(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <VerifyEmailPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

const verifyCalls = (fetchMock: ReturnType<typeof mockFetch>) =>
  fetchMock.mock.calls.filter(([url]) => url === "/api/auth/verify-email");

describe("メールアドレスの確認", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("開いただけでは確認せず、ボタンを押すと確認してログインへ案内する", async () => {
    const fetchMock = mockFetch({ status: 204 });
    renderPage("/verify-email?token=abc");
    const button = await screen.findByRole("button", { name: "確認する" });
    expect(verifyCalls(fetchMock)).toHaveLength(0);

    await userEvent.setup().click(button);

    expect(await screen.findByRole("link", { name: "ログインする" })).toBeTruthy();
    const init = verifyCalls(fetchMock)[0] as unknown as [string, RequestInit];
    expect(JSON.parse(init[1].body as string)).toEqual({ token: "abc" });
  });

  it("期限切れなどはサーバーの理由を出す", async () => {
    mockFetch({ status: 400, body: { message: "リンクが正しくないか、有効期限が切れています" } });
    renderPage("/verify-email?token=old");
    await userEvent.setup().click(await screen.findByRole("button", { name: "確認する" }));
    expect((await screen.findByRole("alert")).textContent).toContain("有効期限が切れています");
  });

  it("トークンが無いリンクではボタンを出さない", async () => {
    mockFetch({ status: 204 });
    renderPage("/verify-email");
    expect((await screen.findByRole("alert")).textContent).toBe("リンクが正しくありません");
    expect(screen.queryByRole("button", { name: "確認する" })).toBeNull();
  });
});
