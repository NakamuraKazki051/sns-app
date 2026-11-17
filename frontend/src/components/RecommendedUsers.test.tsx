import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { api } from "../api";
import { RecommendedUsers, type RecommendedUser } from "./RecommendedUsers";

vi.mock("../api", async (original) => ({ ...(await original<typeof import("../api")>()), api: vi.fn() }));

const user = (username: string, reason: RecommendedUser["reason"], mutualCount = 0): RecommendedUser => ({
  id: username, username, displayName: username.toUpperCase(), iconUpdatedAt: null, bio: null, reason, mutualCount,
});

describe("RecommendedUsers", () => {
  it("おすすめの人と理由を並べ、その人のページへのリンクにする", async () => {
    vi.mocked(api).mockResolvedValue([user("dave", "FOLLOWED_BY_FOLLOWING", 2), user("erin", "POPULAR"), user("fred", "NEW")]);
    render(
      <MemoryRouter>
        <RecommendedUsers />
      </MemoryRouter>,
    );
    const section = await screen.findByRole("region", { name: "おすすめのユーザー" });
    const links = within(section).getAllByRole("link");
    expect(links.map((l) => l.getAttribute("href"))).toEqual(["/@dave", "/@erin", "/@fred"]);
    expect(links[0].textContent).toContain("フォロー中の2人がフォロー");
    expect(links[1].textContent).toContain("人気のユーザー");
    expect(links[2].textContent).toContain("最近はじめた人");
  });

  it("おすすめが無ければ何も出さない", async () => {
    vi.mocked(api).mockResolvedValue([]);
    const { container } = render(
      <MemoryRouter>
        <RecommendedUsers />
      </MemoryRouter>,
    );
    await new Promise((r) => setTimeout(r, 0));
    expect(container.textContent).toBe("");
  });
});
