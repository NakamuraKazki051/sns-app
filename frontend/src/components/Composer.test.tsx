import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Composer } from "./Composer";

// テスト用のブラウザ(jsdom)には画像を描く仕組みが無いので、縮める処理は置き換える(中身は image.ts の役目)
const PREPARED = new Blob(["prepared"], { type: "image/png" });
vi.mock("../image", () => {
  class ImageError extends Error {}
  return {
    ImageError,
    prepareImage: vi.fn(async (file: File) => {
      if (file.type !== "image/png" && file.type !== "image/jpeg") {
        throw new ImageError("画像は JPEG か PNG を選んでください");
      }
      return PREPARED;
    }),
  };
});
// 見本の URL を作る仕組みも jsdom には無い
URL.createObjectURL = () => "blob:preview";
URL.revokeObjectURL = () => {};

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
    expect(onSubmit).toHaveBeenCalledWith("こんにちは", null);
    expect(textarea.value).toBe("");

    onSubmit.mockRejectedValueOnce(new Error("投稿が多すぎます"));
    await userEvent.type(textarea, "もう一回");
    await userEvent.click(button);
    expect(textarea.value).toBe("もう一回");
  });
});

describe("Composer の画像", () => {
  function setupWithImage(onSubmit = vi.fn(async () => {})) {
    render(<Composer placeholder="いまどうしてる？" submitLabel="投稿する" allowImage onSubmit={onSubmit} />);
    return {
      input: screen.getByLabelText("画像を付ける") as HTMLInputElement,
      button: screen.getByRole("button", { name: "投稿する" }) as HTMLButtonElement,
      onSubmit,
    };
  }

  it("画像だけでも送れ、送ったら画像も外れる", async () => {
    const { input, button, onSubmit } = setupWithImage();
    await userEvent.upload(input, new File(["png"], "a.png", { type: "image/png" }));
    expect(await screen.findByAltText("添付する画像")).toBeTruthy();
    expect(button.disabled).toBe(false);

    await userEvent.click(button);
    expect(onSubmit).toHaveBeenCalledWith("", PREPARED);
    await waitFor(() => expect(screen.queryByAltText("添付する画像")).toBeNull());
  });

  it("× で画像を外せる", async () => {
    const { input, button } = setupWithImage();
    await userEvent.upload(input, new File(["png"], "a.png", { type: "image/png" }));
    await userEvent.click(await screen.findByRole("button", { name: "画像を外す" }));
    expect(screen.queryByAltText("添付する画像")).toBeNull();
    expect(button.disabled).toBe(true);
    // ×のボタンが消えても、続けて入力できるよう入力欄へ移る
    expect(document.activeElement).toBe(screen.getByLabelText("いまどうしてる？"));
  });

  it("使えない画像はその場で理由を出す", async () => {
    const { input } = setupWithImage();
    await userEvent.upload(input, new File(["gif"], "a.gif", { type: "image/gif" }), { applyAccept: false });
    expect((await screen.findByRole("alert")).textContent).toBe("画像は JPEG か PNG を選んでください");
  });

  it("リプライ欄には画像のボタンを出さない", () => {
    setup();
    expect(screen.queryByLabelText("画像を付ける")).toBeNull();
  });
});
