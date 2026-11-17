import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../api";
import { ReportDialog } from "./ReportDialog";

const showError = vi.fn();
const showToast = vi.fn();
vi.mock("../api", async (original) => ({ ...(await original<typeof import("../api")>()), api: vi.fn() }));
vi.mock("../feedback", () => ({ useFeedback: () => ({ showError, showToast }) }));

describe("ReportDialog", () => {
  beforeEach(() => vi.clearAllMocks());

  it("理由を選ばないと送らない", async () => {
    render(<ReportDialog target={{ postId: "1" }} onClose={vi.fn()} />);
    await userEvent.click(screen.getByRole("button", { name: "通報する" }));
    expect(showError).toHaveBeenCalledWith("通報の理由を選んでください");
    expect(api).not.toHaveBeenCalled();
  });

  it("投稿を、選んだ理由と説明で通報して閉じる", async () => {
    const onClose = vi.fn();
    vi.mocked(api).mockResolvedValue(undefined);
    render(<ReportDialog target={{ postId: "42" }} onClose={onClose} />);
    expect(screen.getByRole("heading", { name: "投稿を通報" })).toBeTruthy();
    await userEvent.click(screen.getByLabelText("スパム・宣伝"));
    await userEvent.type(screen.getByLabelText("詳しい説明（任意）"), " 同じ宣伝を何度も ");
    await userEvent.click(screen.getByRole("button", { name: "通報する" }));

    expect(api).toHaveBeenCalledWith("/api/reports", {
      method: "POST",
      body: { postId: "42", reason: "SPAM", detail: "同じ宣伝を何度も" },
    });
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(showToast).toHaveBeenCalledWith("通報しました。ご協力ありがとうございます");
  });

  it("ユーザーの通報では説明を省ける", async () => {
    vi.mocked(api).mockResolvedValue(undefined);
    render(<ReportDialog target={{ username: "bob" }} onClose={vi.fn()} />);
    expect(screen.getByRole("heading", { name: "ユーザーを通報" })).toBeTruthy();
    await userEvent.click(screen.getByLabelText("なりすまし"));
    await userEvent.click(screen.getByRole("button", { name: "通報する" }));
    expect(api).toHaveBeenCalledWith("/api/reports", {
      method: "POST",
      body: { username: "bob", reason: "IMPERSONATION", detail: undefined },
    });
  });
});
