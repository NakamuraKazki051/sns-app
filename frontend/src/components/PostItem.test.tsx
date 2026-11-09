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
    image: null,
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

describe("PostItem の画像", () => {
  it("画像があれば枠の大きさを付けて出し、押すと大きく見られる(詳細ページへは移らない)", async () => {
    renderItem(post({ image: { url: "/api/posts/10/image", width: 400, height: 300 } }));
    const img = document.querySelector(".post-image img") as HTMLImageElement;
    expect(img.getAttribute("src")).toBe("/api/posts/10/image");
    expect(img.getAttribute("width")).toBe("400");
    expect(img.getAttribute("height")).toBe("300");

    await userEvent.click(screen.getByRole("button", { name: "画像を大きく見る" }));
    expect(screen.getByRole("dialog", { name: "画像" })).toBeTruthy();
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("dialog", { name: "画像" })).toBeNull();
  });

  it("画像だけの投稿では本文の行を出さない", () => {
    renderItem(post({ body: "", image: { url: "/api/posts/10/image", width: 10, height: 10 } }));
    expect(document.querySelector(".post-text")).toBeNull();
    expect(screen.getByAltText("ボブさんの画像")).toBeTruthy();
  });
});

describe("PostItem の編集(F-19)", () => {
  it("自分の投稿だけ「編集」が出る", () => {
    renderItem(post());
    expect(screen.queryByRole("button", { name: "編集" })).toBeNull();
  });

  it("直した本文を送り、返ってきた投稿で表示を替える", async () => {
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify(post({ user: ME, body: "直した", editedAt: "2026-11-09T00:00:00Z" })), { status: 200 }),
    );
    vi.stubGlobal("fetch", fetchMock);
    const onChange = renderItem(post({ user: ME, body: "直す前" }));

    await userEvent.click(screen.getByRole("button", { name: "編集" }));
    const box = screen.getByRole("textbox", { name: "投稿を編集" });
    expect((box as HTMLTextAreaElement).value).toBe("直す前");
    await userEvent.clear(box);
    await userEvent.type(box, "直した");
    await userEvent.click(screen.getByRole("button", { name: "保存する" }));

    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/posts/10");
    expect(init.method).toBe("PATCH");
    expect(JSON.parse(init.body as string)).toEqual({ body: "直した" });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ body: "直した", editedAt: "2026-11-09T00:00:00Z" }));
    expect(screen.queryByRole("textbox", { name: "投稿を編集" })).toBeNull();
  });

  it("キャンセルすると送らずに元に戻る", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    renderItem(post({ user: ME }));
    await userEvent.click(screen.getByRole("button", { name: "編集" }));
    await userEvent.click(screen.getByRole("button", { name: "キャンセル" }));
    expect(fetchMock).not.toHaveBeenCalled();
    expect(screen.getByText("こんにちは")).toBeTruthy();
  });

  it("直した投稿には「編集済み」を出す", () => {
    renderItem(post({ editedAt: "2026-11-09T00:00:00Z" }));
    expect(screen.getByText("(編集済み)")).toBeTruthy();
  });
});
