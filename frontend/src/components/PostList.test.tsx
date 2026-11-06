import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Post, PublicUser } from "../types";
import type { Paged } from "../usePaged";
import { PostList } from "./PostList";

const ME: PublicUser = { id: "u1", username: "alice", displayName: "アリス", iconUpdatedAt: null };
const BOB: PublicUser = { id: "u2", username: "bob", displayName: "ボブ", iconUpdatedAt: null };

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
    repostsCount: 1,
    repostedByMe: true,
    repostedBy: null,
    image: null,
    ...overrides,
  };
}

function List({ initial }: { initial: Post[] }) {
  const [items, setItems] = useState(initial);
  const paged = { items, setItems, hasMore: false, loading: false, error: null, loadMore: vi.fn(), reload: vi.fn(), retry: vi.fn() };
  return <PostList paged={paged as unknown as Paged<Post>} emptyMessage="まだ投稿がありません" />;
}

afterEach(() => vi.unstubAllGlobals());

describe("PostList のリポストの取り消し", () => {
  it("自分のリポストの行だけ消し、元の投稿の行は残す", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify({ reposted: false, repostsCount: 0 }), { status: 200 })),
    );
    render(
      <MemoryRouter>
        <List initial={[post({ repostedBy: ME }), post()]} />
      </MemoryRouter>,
    );
    expect(screen.queryByText("リポストしました", { exact: false })).not.toBeNull();

    await userEvent.click(screen.getAllByRole("button", { name: "リポストを取り消す" })[0]);

    await screen.findByRole("button", { name: "リポスト" });
    expect(screen.queryByText("リポストしました", { exact: false })).toBeNull();
    expect(screen.getAllByText("こんにちは")).toHaveLength(1);
  });
});
