import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Post, PublicUser } from "../types";
import { PostItem } from "./PostItem";

const ME: PublicUser = { id: "u1", username: "alice", displayName: "アリス", iconUpdatedAt: null };
const BOB: PublicUser = { id: "u2", username: "bob", displayName: "ボブ", iconUpdatedAt: null };
const CAROL: PublicUser = { id: "u3", username: "carol", displayName: "キャロル", iconUpdatedAt: null };

vi.mock("../auth", () => ({ useMe: () => ({ ...ME, email: "alice@example.com", bio: null, createdAt: "" }) }));
vi.mock("../feedback", () => ({ useFeedback: () => ({ showError: vi.fn() }) }));

function post(overrides: Partial<Post> = {}): Post {
  return {
    id: "10",
    body: "こんにちは",
    createdAt: "2026-10-31T00:00:00Z",
    user: BOB,
    likesCount: 0,
    repliesCount: 0,
    likedByMe: false,
    repostsCount: 0,
    repostedByMe: false,
    repostedBy: null,
    ...overrides,
  };
}

function renderItem(p: Post, onChange = vi.fn()) {
  render(
    <MemoryRouter>
      <ul>
        <PostItem post={p} onChange={onChange} onDeleted={vi.fn()} />
      </ul>
    </MemoryRouter>,
  );
  return onChange;
}

afterEach(() => vi.unstubAllGlobals());

describe("PostItem のリポスト", () => {
  it("押すとリポストし、数と押した状態を親に伝える", async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ reposted: true, repostsCount: 3 }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const onChange = renderItem(post({ repostsCount: 2 }));

    await userEvent.click(screen.getByRole("button", { name: "リポスト" }));
    expect(fetchMock).toHaveBeenCalledWith("/api/posts/10/repost", expect.objectContaining({ method: "POST" }));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ repostedByMe: true, repostsCount: 3 }));
  });

  it("リポスト済みなら取り消す", async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ reposted: false, repostsCount: 0 }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    renderItem(post({ repostedByMe: true, repostsCount: 1 }));

    const button = screen.getByRole("button", { name: "リポストを取り消す" });
    expect(button.getAttribute("aria-pressed")).toBe("true");
    await userEvent.click(button);
    expect(fetchMock).toHaveBeenCalledWith("/api/posts/10/repost", expect.objectContaining({ method: "DELETE" }));
  });

  it("自分の投稿はリポストできない", () => {
    renderItem(post({ user: ME, repostsCount: 4 }));
    const button = screen.getByRole("button", { name: "リポスト" }) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain("4");
  });

  it("リポストとして出すときは、誰がリポストしたかを出す", () => {
    renderItem(post({ user: CAROL, repostedBy: BOB }));
    expect(screen.getByText(/さんがリポスト/).textContent).toContain("ボブ");
    expect(screen.getByRole("link", { name: "ボブ" }).getAttribute("href")).toBe("/@bob");
  });

  it("自分のリポストは「リポストしました」", () => {
    renderItem(post({ user: CAROL, repostedBy: ME }));
    expect(screen.getByText(/リポストしました/)).toBeTruthy();
  });
});
