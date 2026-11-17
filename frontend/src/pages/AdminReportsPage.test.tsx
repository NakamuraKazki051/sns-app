import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api, ApiError } from "../api";
import { AdminReportsPage, type AdminReport } from "./AdminReportsPage";

vi.mock("../api", async (original) => ({ ...(await original<typeof import("../api")>()), api: vi.fn() }));
// 本物と同じく、毎回同じ関数を返す(画面は showError が変わると読み直す)
const feedback = { showError: vi.fn(), showToast: vi.fn() };
vi.mock("../feedback", () => ({ useFeedback: () => feedback }));

const user = (username: string) => ({ id: username, username, displayName: username, iconUpdatedAt: null });
const base = { detail: null, createdAt: new Date().toISOString(), reporter: user("alice"), target: user("bob"), resolvedAt: null, resolution: null };
const REPORTS: AdminReport[] = [
  { ...base, id: "1", reason: "SPAM", post: { id: "10", body: "宣伝です" } },
  { ...base, id: "2", reason: "HARASSMENT", post: { id: "10", body: "宣伝です" }, reporter: user("carol") },
  { ...base, id: "3", reason: "IMPERSONATION", post: null, detail: "有名人のふり" },
];

const renderPage = () =>
  render(
    <MemoryRouter>
      <AdminReportsPage />
    </MemoryRouter>,
  );

describe("AdminReportsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api).mockImplementation(async (path) => (String(path).startsWith("/api/admin/reports?") ? REPORTS : undefined));
  });

  it("対応前の通報を、理由・誰が誰を・投稿の本文と並べる", async () => {
    renderPage();
    const items = within(await screen.findByRole("list", { name: "通報" })).getAllByRole("listitem");
    expect(items).toHaveLength(3);
    expect(items[0].textContent).toContain("スパム・宣伝");
    expect(items[0].textContent).toContain("@alice さんが@bob さんの投稿を通報");
    expect(items[0].textContent).toContain("宣伝です");
    expect(items[2].textContent).toContain("アカウントを通報");
    expect(items[2].textContent).toContain("有名人のふり");
    // アカウントの通報には「投稿を削除」が無い
    expect(within(items[2]).queryByRole("button", { name: "投稿を削除" })).toBeNull();
  });

  it("投稿を消すと、同じ投稿への通報も一覧から消える", async () => {
    renderPage();
    const list = await screen.findByRole("list", { name: "通報" });
    await userEvent.click(within(within(list).getAllByRole("listitem")[0]).getByRole("button", { name: "投稿を削除" }));
    expect(api).toHaveBeenCalledWith("/api/admin/reports/1/resolve", { method: "POST", body: { resolution: "POST_DELETED" } });
    await waitFor(() => expect(within(list).getAllByRole("listitem")).toHaveLength(1));
  });

  it("管理者でなければ「見つかりません」", async () => {
    vi.mocked(api).mockRejectedValue(new ApiError(404, "見つかりません"));
    renderPage();
    expect(await screen.findByText("ページが見つかりません")).toBeTruthy();
  });
});
