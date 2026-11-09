import { describe, expect, it } from "vitest";
import { splitHashtags } from "./hashtags";

describe("splitHashtags(F-22)", () => {
  it("タグだけを取り出し、ほかの文字はそのまま残す", () => {
    expect(splitHashtags("#旅行 で #Tokyo_2026 へ。＃ラーメン")).toEqual([
      { text: "#旅行", tag: "旅行" },
      { text: " で " },
      { text: "#Tokyo_2026", tag: "tokyo_2026" },
      { text: " へ。" },
      { text: "＃ラーメン", tag: "ラーメン" },
    ]);
  });

  it("単語の途中の #・数字だけはタグにしない(サーバーと同じ)", () => {
    expect(splitHashtags("C# と a#b と #123")).toEqual([{ text: "C# と a#b と #123" }]);
  });

  it("タグが無ければ1つの文字のまま", () => {
    expect(splitHashtags("ふつうの投稿")).toEqual([{ text: "ふつうの投稿" }]);
  });
});
