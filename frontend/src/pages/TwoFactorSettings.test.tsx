import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../api";
import { groupSecret, TwoFactorSettings } from "./TwoFactorSettings";

const showError = vi.fn();
const showToast = vi.fn();

vi.mock("../api", async (original) => ({ ...(await original<typeof import("../api")>()), api: vi.fn() }));
vi.mock("../feedback", () => ({
  useFeedback: () => ({ showError, clearError: vi.fn(), showToast }),
}));
vi.mock("qrcode", () => ({ default: { toDataURL: vi.fn(async () => "data:image/png;base64,QR") } }));

describe("2段階認証の設定(F-24)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("鍵は4文字ずつ区切って見せる", () => {
    expect(groupSecret("ABCDEFGHIJ")).toBe("ABCD EFGH IJ");
  });

  it("設定を始めると QR コードと鍵を出し、コードが合えばリカバリーコードを一度だけ見せる", async () => {
    vi.mocked(api).mockImplementation(async (path) => {
      if (path === "/api/me/two-factor") return { enabled: false, recoveryCodesLeft: 0 };
      if (path === "/api/me/two-factor/setup") return { secret: "ABCDEFGH", uri: "otpauth://totp/RaiseLine:a" };
      if (path === "/api/me/two-factor/enable") return { recoveryCodes: ["aaaa-bbbb", "cccc-dddd"] };
      throw new Error(`想定外: ${path}`);
    });
    const user = userEvent.setup();
    render(<TwoFactorSettings />);

    await user.click(await screen.findByRole("button", { name: "2段階認証を設定する" }));
    expect(await screen.findByAltText("認証アプリに読み取らせる QR コード")).toBeTruthy();
    expect(screen.getByText("ABCD EFGH")).toBeTruthy();

    await user.click(screen.getByRole("button", { name: "有効にする" }));
    expect(showError).toHaveBeenCalledWith("認証コードを入力してください");

    await user.type(screen.getByLabelText(/6桁のコード/), "123456");
    await user.click(screen.getByRole("button", { name: "有効にする" }));
    expect(await screen.findByText("aaaa-bbbb")).toBeTruthy();
    expect(vi.mocked(api)).toHaveBeenCalledWith("/api/me/two-factor/enable", { method: "POST", body: { code: "123456" } });

    await user.click(screen.getByRole("button", { name: "保管しました" }));
    expect(screen.queryByText("aaaa-bbbb")).toBeNull();
    expect(screen.getByText("有効")).toBeTruthy();
    expect(screen.getByText(/残りのリカバリーコード：2個/)).toBeTruthy();
  });

  it("外すにはパスワードとコードを送る", async () => {
    vi.mocked(api).mockImplementation(async (path) => {
      if (path === "/api/me/two-factor") return { enabled: true, recoveryCodesLeft: 10 };
      return undefined;
    });
    const user = userEvent.setup();
    render(<TwoFactorSettings />);

    await user.click(await screen.findByRole("button", { name: "2段階認証を外す" }));
    expect(showError).toHaveBeenCalledWith("パスワードを入力してください");

    await user.type(screen.getByLabelText(/パスワード/), "secret-pass");
    await user.type(screen.getByLabelText(/認証コード/), "654321");
    await user.click(screen.getByRole("button", { name: "2段階認証を外す" }));
    await waitFor(() =>
      expect(vi.mocked(api)).toHaveBeenCalledWith("/api/me/two-factor", {
        method: "DELETE",
        body: { password: "secret-pass", code: "654321" },
      }),
    );
    expect(await screen.findByRole("button", { name: "2段階認証を設定する" })).toBeTruthy();
  });
});
