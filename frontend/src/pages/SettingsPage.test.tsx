import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../api";
import type { Me } from "../types";
import { SettingsPage } from "./SettingsPage";

const ME: Me = {
  id: "u1", email: "a@example.com", username: "alice", displayName: "アリス", iconUpdatedAt: null, bio: null, createdAt: "",
};
const updateMe = vi.fn();
const deleteAccount = vi.fn();
const showError = vi.fn();

vi.mock("../api", async (original) => ({ ...(await original<typeof import("../api")>()), api: vi.fn() }));
vi.mock("../auth", () => ({ useMe: () => ME, useAuth: () => ({ updateMe, deleteAccount }) }));
vi.mock("../feedback", () => ({
  useFeedback: () => ({ showError, clearError: vi.fn(), showToast: vi.fn() }),
}));
URL.createObjectURL = () => "blob:preview";
URL.revokeObjectURL = () => {};

describe("SettingsPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("アイコンの保存だけ失敗しても、保存できた表示名は画面に反映する", async () => {
    const saved = { ...ME, displayName: "新しい名前" };
    vi.mocked(api).mockImplementation(async (path) => {
      if (path === "/api/me/profile") return saved;
      throw new Error("アイコンを保存できません");
    });
    const { container } = render(<SettingsPage />);
    const file = container.querySelector('input[type="file"]') as HTMLInputElement;
    await userEvent.upload(file, new File(["x"], "a.png", { type: "image/png" }));

    await userEvent.click(screen.getByRole("button", { name: "保存する" }));

    await waitFor(() => expect(showError).toHaveBeenCalledWith("アイコンを保存できません"));
    expect(updateMe).toHaveBeenCalledWith(saved);
  });

  it("アカウントの削除は、パスワードを入れて確認のダイアログで押したときだけ送る", async () => {
    const { container } = render(<SettingsPage />);
    const deleteButton = screen.getByRole("button", { name: "アカウントを削除する" });

    await userEvent.click(deleteButton);
    expect(showError).toHaveBeenCalledWith("パスワードを入力してください");
    expect(screen.queryByRole("dialog")).toBeNull();

    await userEvent.type(container.querySelector('input[name="deletePassword"]') as HTMLInputElement, "secret-pass");
    await userEvent.click(deleteButton);
    await userEvent.click(screen.getByRole("button", { name: "キャンセル" }));
    expect(deleteAccount).not.toHaveBeenCalled();

    await userEvent.click(deleteButton);
    await userEvent.click(screen.getByRole("button", { name: "削除する" }));
    expect(deleteAccount).toHaveBeenCalledWith("secret-pass");
  });

  it("パスワードが違えば、サーバーのメッセージを出す", async () => {
    deleteAccount.mockRejectedValueOnce(new Error("パスワードが正しくありません"));
    const { container } = render(<SettingsPage />);
    await userEvent.type(container.querySelector('input[name="deletePassword"]') as HTMLInputElement, "wrong-pass");
    await userEvent.click(screen.getByRole("button", { name: "アカウントを削除する" }));
    await userEvent.click(screen.getByRole("button", { name: "削除する" }));
    await waitFor(() => expect(showError).toHaveBeenCalledWith("パスワードが正しくありません"));
  });
});
