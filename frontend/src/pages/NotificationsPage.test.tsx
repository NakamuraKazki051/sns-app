import { render, screen } from "@testing-library/react";
import { MemoryRouter, Outlet, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Notification } from "../types";
import { NotificationsPage } from "./NotificationsPage";

const BOB = { id: "u2", username: "bob", displayName: "ボブ", iconUpdatedAt: null };

function notification(overrides: Partial<Notification>): Notification {
  return { id: "1", type: "LIKE", actor: BOB, post: { id: "10", body: "元の投稿" }, createdAt: "2026-11-09T00:00:00Z", read: false, ...overrides };
}

function mockFetch(list: Notification[]) {
  const fetchMock = vi.fn(async (url: string) =>
    url === "/api/notifications/read"
      ? new Response(null, { status: 204 })
      : new Response(JSON.stringify({ notifications: list, hasMore: false, nextCursor: null }), { status: 200 }),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function renderPage(onNotificationsRead = vi.fn()) {
  render(
    <MemoryRouter initialEntries={["/notifications"]}>
      <Routes>
        <Route element={<Outlet context={{ onNotificationsRead }} />}>
          <Route path="/notifications" element={<NotificationsPage />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
  return onNotificationsRead;
}

afterEach(() => vi.unstubAllGlobals());

describe("通知の画面(F-21)", () => {
  it("種類ごとの文と、どの投稿への反応かを出し、開いたら既読にする", async () => {
    const fetchMock = mockFetch([
      notification({ id: "3", type: "FOLLOW", post: null }),
      notification({ id: "2", type: "REPLY", read: true }),
      notification({ id: "1", type: "LIKE" }),
    ]);
    const onRead = renderPage();

    expect(await screen.findByText("さんがあなたをフォローしました")).toBeTruthy();
    expect(screen.getByText("さんがあなたの投稿にリプライしました")).toBeTruthy();
    expect(screen.getByText("さんがあなたの投稿にいいねしました")).toBeTruthy();
    expect(screen.getAllByRole("link", { name: "元の投稿" })[0].getAttribute("href")).toBe("/posts/10");
    expect(document.querySelectorAll(".notification.unread")).toHaveLength(2);
    await vi.waitFor(() => expect(onRead).toHaveBeenCalled());
    expect(fetchMock.mock.calls.some(([url]) => url === "/api/notifications/read")).toBe(true);
  });

  it("無ければそう伝える", async () => {
    mockFetch([]);
    renderPage();
    expect(await screen.findByText("まだ通知はありません")).toBeTruthy();
  });
});
