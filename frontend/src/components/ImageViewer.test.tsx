import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { ImageViewer } from "./ImageViewer";

function Page() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>
        開く
      </button>
      {open && <ImageViewer src="/a.png" alt="画像" onClose={() => setOpen(false)} />}
    </>
  );
}

describe("ImageViewer", () => {
  it("開くと閉じるボタンへ移り、Esc で閉じると開いたボタンへ戻る", async () => {
    render(<Page />);
    const opener = screen.getByRole("button", { name: "開く" });
    await userEvent.click(opener);
    expect(document.activeElement).toBe(screen.getByRole("button", { name: "閉じる" }));

    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(document.activeElement).toBe(opener);
  });

  it("開いている間は Tab で後ろの画面へ移らない", async () => {
    render(<Page />);
    await userEvent.click(screen.getByRole("button", { name: "開く" }));
    await userEvent.tab();
    expect(document.activeElement).toBe(screen.getByRole("button", { name: "閉じる" }));
  });
});
