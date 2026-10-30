import { describe, expect, it } from "vitest";
import { countChars, displayNameLength, relativeTime, safeNext } from "./text";

describe("countChars", () => {
  it("絵文字や結合文字も見た目の1文字で数える", () => {
    expect(countChars("あいう")).toBe(3);
    expect(countChars("😀".repeat(140))).toBe(140);
    expect(countChars("👍🏽")).toBe(1);
    expect(countChars("👨‍👩‍👧")).toBe(1);
    expect(countChars("")).toBe(0);
  });
});

describe("displayNameLength", () => {
  it("サーバーと同じく UTF-16 の単位で数える", () => {
    expect(displayNameLength("あいう")).toBe(3);
    expect(displayNameLength("😀")).toBe(2);
  });
});

describe("relativeTime", () => {
  const now = Date.parse("2026-10-30T12:00:00+09:00");
  const ago = (ms: number) => new Date(now - ms).toISOString();

  it("1分未満・分・時間・日付の順に表示が変わる", () => {
    expect(relativeTime(ago(30_000), now)).toBe("たった今");
    expect(relativeTime(ago(3 * 60_000), now)).toBe("3分前");
    expect(relativeTime(ago(5 * 3_600_000), now)).toBe("5時間前");
    expect(relativeTime("2026-10-28T09:00:00+09:00", now)).toBe("10月28日");
    expect(relativeTime("2025-10-28T09:00:00+09:00", now)).toBe("2025年10月28日");
  });

  it("サーバーと時計がずれて未来の時刻でも「たった今」", () => {
    expect(relativeTime(new Date(now + 5_000).toISOString(), now)).toBe("たった今");
  });
});

describe("safeNext", () => {
  it("サイト内のパスだけ許し、外部サイトへは移さない", () => {
    expect(safeNext("/@alice")).toBe("/@alice");
    expect(safeNext("/search?q=a")).toBe("/search?q=a");
    expect(safeNext(null)).toBe("/");
    expect(safeNext("https://evil.example")).toBe("/");
    expect(safeNext("//evil.example")).toBe("/");
    expect(safeNext("/\\evil.example")).toBe("/");
  });
});
