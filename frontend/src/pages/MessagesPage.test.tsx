import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api, ApiError } from "../api";
import type { DirectMessage, Me } from "../types";
import { ConversationPage, MessagesPage, mergeMessages } from "./MessagesPage";

const ME: Me = {
  id: "u1", email: "a@example.com", username: "alice", displayName: "アリス", iconUpdatedAt: null, bio: null, createdAt: "",
};
const BOB = { id: "u2", username: "bob", displayName: "ボブ", iconUpdatedAt: null };
const PROFILE = { ...BOB, bio: null, postsCount: 0, followingCount: 0, followersCount: 0, followedByMe: false, createdAt: "", blocking: false, muting: false };
const showError = vi.fn();

vi.mock("../api", async (original) => ({ ...(await original<typeof import("../api")>()), api: vi.fn() }));
vi.mock("../auth", () => ({ useMe: () => ME }));
vi.mock("../feedback", () => ({
  useFeedback: () => ({ showError, clearError: vi.fn(), showToast: vi.fn() }),
}));

function message(id: string, overrides: Partial<DirectMessage> = {}): DirectMessage {
  return { id, mine: false, body: `本文${id}`, createdAt: "2026-11-10T00:00:00Z", read: false, ...overrides };
}

function renderAt(path: string, onMessagesRead = vi.fn()) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<Outlet context={{ onMessagesRead }} />}>
          <Route path="/messages" element={<MessagesPage />} />
          <Route path="/messages/:username" element={<ConversationPage />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
  return onMessagesRead;
}

describe("mergeMessages", () => {
  it("古い順に並べ、同じ ID は新しい方(既読が付いたもの)で置き換える", () => {
    const merged = mergeMessages([message("2", { mine: true }), message("10")], [message("2", { mine: true, read: true }), message("11"), message("1")]);
    expect(merged.map((m) => m.id)).toEqual(["1", "2", "10", "11"]);
    expect(merged[1].read).toBe(true);
  });
});

describe("DM の画面(F-23)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("会話一覧に、相手・最後のメッセージ・未読の数を出す", async () => {
    vi.mocked(api).mockResolvedValue([
      { partner: BOB, lastMessage: message("3", { body: "またね" }), unreadCount: 2 },
      { partner: { ...BOB, id: "u3", username: "carol", displayName: "キャロル" }, lastMessage: message("1", { mine: true, body: "やあ" }), unreadCount: 0 },
    ]);
    renderAt("/messages");
    expect(await screen.findByText("またね")).toBeTruthy();
    expect(screen.getByText("あなた: やあ")).toBeTruthy();
    expect(screen.getByLabelText("未読 2件")).toBeTruthy();
    expect(screen.getAllByRole("link")[0].getAttribute("href")).toBe("/messages/bob");
  });

  it("会話が無ければ、送り方を伝える", async () => {
    vi.mocked(api).mockResolvedValue([]);
    renderAt("/messages");
    expect(await screen.findByText("まだメッセージはありません")).toBeTruthy();
  });

  it("会話を古い順に出し、自分の最後のメッセージに既読を付け、送ると下に足す", async () => {
    vi.mocked(api).mockImplementation(async (path, options) => {
      if (path === "/api/users/bob") return PROFILE;
      if (path === "/api/dm/bob" && options?.method === "POST") return message("4", { mine: true, body: "了解" });
      if (path === "/api/dm/bob") {
        return { messages: [message("3"), message("2", { mine: true, read: true }), message("1")], hasMore: false, nextCursor: null };
      }
      throw new Error(`想定外: ${path}`);
    });
    const onRead = renderAt("/messages/bob");

    expect(await screen.findByText("本文1")).toBeTruthy();
    const bubbles = () => [...document.querySelectorAll(".dm-bubble")].map((b) => b.textContent);
    expect(bubbles()).toEqual(["本文1", "本文2", "本文3"]);
    expect(screen.getByText(/既読/)).toBeTruthy();
    expect(onRead).toHaveBeenCalled();

    await userEvent.type(screen.getByRole("textbox", { name: "メッセージを入力" }), "了解");
    await userEvent.click(screen.getByRole("button", { name: "送信" }));
    await waitFor(() => expect(bubbles()).toEqual(["本文1", "本文2", "本文3", "了解"]));
    expect(vi.mocked(api)).toHaveBeenCalledWith("/api/dm/bob", { method: "POST", body: { body: "了解" } });
    expect(screen.getByText(/送信済み/)).toBeTruthy();
  });

  it("ブロックなどで相手が見つからなければ、そう伝える", async () => {
    vi.mocked(api).mockRejectedValue(new ApiError(404, "ユーザーが見つかりません"));
    renderAt("/messages/bob");
    expect(await screen.findByText("ユーザーが見つかりません: @bob")).toBeTruthy();
  });

  it("自分あての会話は開かない", () => {
    renderAt("/messages/alice");
    expect(screen.getByText("自分にはメッセージを送れません")).toBeTruthy();
    expect(api).not.toHaveBeenCalled();
  });
});
