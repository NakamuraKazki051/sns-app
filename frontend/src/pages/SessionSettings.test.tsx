import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../api";
import { SessionSettings, type Session } from "./SessionSettings";

const logout = vi.fn();
const showToast = vi.fn();
vi.mock("../api", async (original) => ({ ...(await original<typeof import("../api")>()), api: vi.fn() }));
vi.mock("../auth", () => ({ useAuth: () => ({ logout }) }));
vi.mock("../feedback", () => ({ useFeedback: () => ({ showError: vi.fn(), showToast }) }));

const now = new Date().toISOString();
const SESSIONS: Session[] = [
  { id: "s1", device: "Chrome(Windows)", createdAt: now, lastUsedAt: now, current: true },
  { id: "s2", device: "Safari(iPhone)", createdAt: now, lastUsedAt: now, current: false },
  { id: "s3", device: "Firefox(Mac)", createdAt: now, lastUsedAt: now, current: false },
];

describe("SessionSettings", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api).mockImplementation(async (path, options) =>
      path === "/api/me/sessions" && !options ? SESSIONS : undefined,
    );
  });

  it("端末を並べ、この端末に印を付ける", async () => {
    render(<SessionSettings />);
    const list = await screen.findByRole("list", { name: "ログイン中の端末" });
    const items = within(list).getAllByRole("listitem");
    expect(items).toHaveLength(3);
    expect(items[0].textContent).toContain("Chrome(Windows)この端末");
    expect(items[1].textContent).not.toContain("この端末");
  });

  it("選んだ端末だけログアウトさせる", async () => {
    render(<SessionSettings />);
    const list = await screen.findByRole("list", { name: "ログイン中の端末" });
    const iphone = within(list).getAllByRole("listitem")[1];
    await userEvent.click(within(iphone).getByRole("button", { name: "Safari(iPhone)をログアウト" }));

    expect(api).toHaveBeenCalledWith("/api/me/sessions/s2", { method: "DELETE" });
    await waitFor(() => expect(within(list).getAllByRole("listitem")).toHaveLength(2));
    expect(list.textContent).not.toContain("Safari(iPhone)");
    expect(logout).not.toHaveBeenCalled();
  });

  it("この端末を選んだら、ふつうのログアウト", async () => {
    render(<SessionSettings />);
    const list = await screen.findByRole("list", { name: "ログイン中の端末" });
    await userEvent.click(within(within(list).getAllByRole("listitem")[0]).getByRole("button", { name: "この端末をログアウト" }));
    expect(logout).toHaveBeenCalledOnce();
  });

  it("ほかの端末をまとめてログアウトさせる(確認あり)", async () => {
    render(<SessionSettings />);
    await userEvent.click(await screen.findByRole("button", { name: "ほかの端末をすべてログアウト" }));
    expect(screen.getByText("この端末以外の2台がログアウトします。")).toBeTruthy();
    await userEvent.click(screen.getByRole("button", { name: "ログアウトさせる" }));

    expect(api).toHaveBeenCalledWith("/api/me/sessions", { method: "DELETE" });
    const list = screen.getByRole("list", { name: "ログイン中の端末" });
    await waitFor(() => expect(within(list).getAllByRole("listitem")).toHaveLength(1));
    expect(screen.queryByRole("button", { name: "ほかの端末をすべてログアウト" })).toBeNull();
  });
});
