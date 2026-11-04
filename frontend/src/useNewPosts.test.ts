import { describe, expect, it } from "vitest";
import type { Post, PublicUser } from "./types";
import { isNewerId, latestPostId } from "./useNewPosts";

const BOB: PublicUser = { id: "u2", username: "bob", displayName: "ボブ", iconUpdatedAt: null };

function post(id: string, repostedBy: PublicUser | null = null): Post {
  return {
    id, body: "x", createdAt: "", user: BOB, likesCount: 0, repliesCount: 0, likedByMe: false,
    repostsCount: 0, repostedByMe: false, repostedBy, image: null,
  };
}

describe("latestPostId", () => {
  it("リポストの行を除いた、一番大きい ID", () => {
    expect(latestPostId([post("9"), post("50", BOB), post("12"), post("3")])).toBe("12");
  });

  it("空なら 0", () => {
    expect(latestPostId([])).toBe("0");
  });

  it("桁が大きくても正しく比べる(文字列の比較や Number の誤差にならない)", () => {
    expect(latestPostId([post("9"), post("10")])).toBe("10");
    expect(isNewerId("9007199254740993", "9007199254740992")).toBe(true);
    expect(isNewerId("9", "10")).toBe(false);
  });
});
