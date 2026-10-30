import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Composer } from "./Composer";

function setup(onSubmit = vi.fn(async () => {})) {
  render(<Composer placeholder="いまどうしてる？" submitLabel="投稿する" onSubmit={onSubmit} />);
  return {
    textarea: screen.getByLabelText("いまどうしてる？") as HTMLTextAreaElement,
    button: screen.getByRole("button", { name: "投稿する" }) as HTMLButtonElement,
    onSubmit,
  };
}

describe("Composer", () => {
  it("空や空白だけでは送れない", async () => {
    const { textarea, button } = setup();
    expect(button.disabled).toBe(true);
    await userEvent.type(textarea, "   ");
    expect(button.disabled).toBe(true);
  });

  it("140字ちょうど(絵文字を含む)は送れ、141字は送れない", async () => {
    const { textarea, button } = setup();
    await userEvent.click(textarea);
    await userEvent.paste("😀".repeat(140));
    expect(screen.getByText("0")).toBeTruthy();
    expect(button.disabled).toBe(false);

    await userEvent.type(textarea, "あ");
    expect(screen.getByText("-1").className).toContain("over");
    expect(button.disabled).toBe(true);
  });

  it("送れたら入力欄を空にし、失敗したら入力を残す", async () => {
    const onSubmit = vi.fn(async () => {});
    const { textarea, button } = setup(onSubmit);
    await userEvent.type(textarea, "こんにちは");
    await userEvent.click(button);
    expect(onSubmit).toHaveBeenCalledWith("こんにちは");
    expect(textarea.value).toBe("");

    onSubmit.mockRejectedValueOnce(new Error("投稿が多すぎます"));
    await userEvent.type(textarea, "もう一回");
    await userEvent.click(button);
    expect(textarea.value).toBe("もう一回");
  });
});
