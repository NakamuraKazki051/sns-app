import { describe, expect, it } from "vitest";
import { repliesInOrder } from "./loaders";
import type { Reply } from "./types";

const reply = (id: string) => ({ id }) as Reply;

describe("repliesInOrder", () => {
  it("続きが残っているときに書いたリプライも、続きを読むと正しい位置に入る", () => {
    const ids = repliesInOrder([reply("1"), reply("50"), reply("21")]).map((r) => r.id);
    expect(ids).toEqual(["1", "21", "50"]);
  });

  it("桁の大きい ID も数として比べる", () => {
    const ids = repliesInOrder([reply("9007199254740993"), reply("9007199254740992"), reply("100")]).map((r) => r.id);
    expect(ids).toEqual(["100", "9007199254740992", "9007199254740993"]);
  });
});
