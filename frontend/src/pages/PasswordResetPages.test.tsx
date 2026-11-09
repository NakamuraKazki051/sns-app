import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ForgotPasswordPage, ResetPasswordPage } from "./PasswordResetPages";

function mockFetch(status: number, body?: unknown) {
  const fetchMock = vi.fn(async () => new Response(body === undefined ? null : JSON.stringify(body), { status }));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

const sentBody = (fetchMock: ReturnType<typeof mockFetch>) =>
  JSON.parse((fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1].body as string);

const field = (name: string) => document.querySelector(`input[name="${name}"]`) as HTMLInputElement;

afterEach(() => vi.unstubAllGlobals());

describe("パスワードを忘れた", () => {
  it("送ると、登録の有無が分からない文で知らせる", async () => {
    const fetchMock = mockFetch(204);
    render(<MemoryRouter><ForgotPasswordPage /></MemoryRouter>);
    await userEvent.type(screen.getByLabelText("メールアドレス"), "a@example.com");
    await userEvent.click(screen.getByRole("button", { name: "リンクを送る" }));

    expect(await screen.findByText(/a@example.com が登録されていれば/)).toBeTruthy();
    expect(sentBody(fetchMock)).toEqual({ email: "a@example.com" });
  });

  it("空では送らない", async () => {
    const fetchMock = mockFetch(204);
    render(<MemoryRouter><ForgotPasswordPage /></MemoryRouter>);
    await userEvent.click(screen.getByRole("button", { name: "リンクを送る" }));
    expect(screen.getByRole("alert").textContent).toBe("メールアドレスを入力してください");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("新しいパスワードを決める", () => {
  const renderPage = (path: string) =>
    render(<MemoryRouter initialEntries={[path]}><ResetPasswordPage /></MemoryRouter>);

  it("リンクのトークンと新しいパスワードを送る", async () => {
    const fetchMock = mockFetch(204);
    renderPage("/reset-password?token=abc");
    await userEvent.type(field("newPassword"), "new-password-1");
    await userEvent.type(field("confirmPassword"), "new-password-1");
    await userEvent.click(screen.getByRole("button", { name: "変更する" }));

    expect(await screen.findByText("パスワードを変更しました")).toBeTruthy();
    expect(sentBody(fetchMock)).toEqual({ token: "abc", newPassword: "new-password-1" });
  });

  it("確認用が違う・短いときは送らない", async () => {
    const fetchMock = mockFetch(204);
    renderPage("/reset-password?token=abc");
    await userEvent.type(field("newPassword"), "short");
    await userEvent.click(screen.getByRole("button", { name: "変更する" }));
    expect(screen.getByRole("alert").textContent).toBe("パスワードは8〜72文字で入力してください");

    await userEvent.type(field("newPassword"), "-but-long-now");
    await userEvent.type(field("confirmPassword"), "different-one");
    await userEvent.click(screen.getByRole("button", { name: "変更する" }));
    expect(screen.getByRole("alert").textContent).toBe("新しいパスワードが確認用と一致しません");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("期限切れのリンクはサーバーのメッセージを出す", async () => {
    mockFetch(400, { message: "リンクが正しくないか、有効期限が切れています" });
    renderPage("/reset-password?token=old");
    await userEvent.type(field("newPassword"), "new-password-1");
    await userEvent.type(field("confirmPassword"), "new-password-1");
    await userEvent.click(screen.getByRole("button", { name: "変更する" }));
    expect((await screen.findByRole("alert")).textContent).toBe("リンクが正しくないか、有効期限が切れています");
  });

  it("トークンの無いリンクは、送り直しへ案内する", () => {
    renderPage("/reset-password");
    expect(screen.getByRole("alert").textContent).toBe("リンクが正しくありません");
    expect(screen.getByRole("link", { name: "再設定のメールを送り直す" })).toBeTruthy();
  });
});
