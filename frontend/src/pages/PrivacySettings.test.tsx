import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../api";
import type { Me } from "../types";
import { PrivacySettings } from "./PrivacySettings";

let me: Me;
const updateMe = vi.fn((next: Me) => {
  me = next;
});
vi.mock("../api", async (original) => ({ ...(await original<typeof import("../api")>()), api: vi.fn() }));
vi.mock("../auth", () => ({ useMe: () => me, useAuth: () => ({ updateMe }) }));
const feedback = { showError: vi.fn(), showToast: vi.fn() };
vi.mock("../feedback", () => ({ useFeedback: () => feedback }));

const bob = { id: "b", username: "bob", displayName: "ボブ", iconUpdatedAt: null, bio: null };
const renderIt = () =>
  render(
    <MemoryRouter>
      <PrivacySettings />
    </MemoryRouter>,
  );

describe("PrivacySettings", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    me = { id: "a", email: "a@example.com", username: "alice", displayName: "アリス", iconUpdatedAt: null, bio: null, createdAt: "", privateAccount: false };
  });

  it("チェックを入れると鍵アカウントにする", async () => {
    vi.mocked(api).mockImplementation(async (path, options) =>
      path === "/api/me/profile" ? { ...me, privateAccount: (options?.body as { privateAccount: boolean }).privateAccount } : [],
    );
    renderIt();
    await userEvent.click(screen.getByRole("checkbox", { name: "投稿を、承認したフォロワーだけに見せる" }));
    expect(api).toHaveBeenCalledWith("/api/me/profile", { method: "PATCH", body: { privateAccount: true } });
    await waitFor(() => expect(updateMe).toHaveBeenCalledWith(expect.objectContaining({ privateAccount: true })));
  });

  it("届いたリクエストを承認すると一覧から消える", async () => {
    me = { ...me, privateAccount: true };
    vi.mocked(api).mockImplementation(async (path) => (path === "/api/me/follow-requests" ? [bob] : undefined));
    renderIt();
    const list = await screen.findByRole("list", { name: "フォローのリクエスト" });
    await userEvent.click(within(list).getByRole("button", { name: "承認" }));
    expect(api).toHaveBeenCalledWith("/api/me/follow-requests/bob", { method: "POST" });
    await waitFor(() => expect(screen.getByText("届いているリクエストはありません")).toBeTruthy());
  });

  it("拒否もできる", async () => {
    me = { ...me, privateAccount: true };
    vi.mocked(api).mockImplementation(async (path) => (path === "/api/me/follow-requests" ? [bob] : undefined));
    renderIt();
    const list = await screen.findByRole("list", { name: "フォローのリクエスト" });
    await userEvent.click(within(list).getByRole("button", { name: "拒否" }));
    expect(api).toHaveBeenCalledWith("/api/me/follow-requests/bob", { method: "DELETE" });
  });
});
