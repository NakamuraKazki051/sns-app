import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthImage } from "./AuthImage";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("AuthImage", () => {
  it("読み込みに失敗したら、作り直してから1回だけ読み直す", async () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    render(<AuthImage src="/api/posts/1/image" alt="画像" />);
    const img = screen.getByAltText("画像");

    fireEvent.error(img);
    await waitFor(() => expect(img.getAttribute("src")).toBe("/api/posts/1/image?retry=1"));
    expect(fetchMock).toHaveBeenCalledWith("/api/auth/refresh", expect.objectContaining({ method: "POST" }));

    // 読み直しても失敗したら(画像が無いなど)、それ以上は作り直さない
    fireEvent.error(img);
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("作り直せなければ、読み直さない", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(null, { status: 401 })));
    render(<AuthImage src="/api/users/alice/icon?v=1" alt="アイコン" />);
    const img = screen.getByAltText("アイコン");

    fireEvent.error(img);
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(img.getAttribute("src")).toBe("/api/users/alice/icon?v=1");
  });
});
