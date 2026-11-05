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

  it("この画面で足した自分の投稿は基準にしない(その前に出た他の人の投稿を見逃さない)", () => {
    expect(latestPostId([{ ...post("31"), addedHere: true }, post("20")])).toBe("20");
  });

  it("リポストの行しか無ければ、その中で一番大きい ID(0 にすると古い投稿まで新着に数えてしまう)", () => {
    expect(latestPostId([post("50", BOB), post("12", BOB)])).toBe("50");
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
