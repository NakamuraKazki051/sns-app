import { randomBytes } from "node:crypto";
import { readFileSync } from "node:fs";
import { expect, test as base, type BrowserContext, type Locator, type Page } from "@playwright/test";

export { expect };

const APP_LOG = process.env.E2E_APP_LOG ?? "app.log";

export type Account = { username: string; displayName: string; email: string; password: string };
type OpenSession = (account?: Account) => Promise<Page>;

/**
 * session: ユーザーごとに別のブラウザのセッションを開く。
 * 画面の中で捕まえられなかった例外があれば、操作が通っていてもテストを失敗にする
 */
export const test = base.extend<{ session: OpenSession }>({
  session: async ({ browser }, use) => {
    const contexts: BrowserContext[] = [];
    const errors: string[] = [];
    await use(async (account) => {
      const context = await browser.newContext();
      contexts.push(context);
      const page = await context.newPage();
      page.on("pageerror", (e) => errors.push(e.message));
      if (account) await logIn(page, account);
      return page;
    });
    await Promise.all(contexts.map((context) => context.close()));
    expect(errors).toEqual([]);
  },
});

/** テストのたびに別のユーザーを作る。名前は5文字まで */
export function newAccount(name: string): Account {
  const id = randomBytes(4).toString("hex");
  return {
    username: `${name}_${id}`,
    displayName: `${name} ${id}`,
    email: `${name}.${id}@example.com`,
    password: "e2e-test-password",
  };
}

/** 毎回違う本文 */
export function uniqueText(text: string): string {
  return `${text} ${randomBytes(4).toString("hex")}`;
}

export async function register(page: Page, account: Account) {
  await page.goto("/login");
  await page.getByRole("tab", { name: "新規登録" }).click();
  await page.locator("input[name=username]").fill(account.username);
  await page.locator("input[name=displayName]").fill(account.displayName);
  await page.locator("input[name=email]").fill(account.email);
  await page.locator("input[name=password]").fill(account.password);
  await page.getByRole("button", { name: "登録してはじめる" }).click();
  await expect(page.getByRole("heading", { name: "メールを確認してください" })).toBeVisible();
}

/** 確認メールの代わりに、アプリのログに出たリンクを読む。同じアドレスなら一番新しいもの */
export async function verificationLink(email: string): Promise<string> {
  const pattern = new RegExp(`${email.replace(/[.+]/g, "\\$&")} (http\\S+)`, "g");
  let link: string | undefined;
  await expect
    .poll(() => {
      link = [...readFileSync(APP_LOG, "utf8").matchAll(pattern)].at(-1)?.[1];
      return link;
    })
    .toBeTruthy();
  return link!;
}

export async function openVerificationLink(page: Page, email: string) {
  await page.goto(await verificationLink(email));
  await page.getByRole("button", { name: "確認する" }).click();
  await expect(page.getByText("確認が終わりました。")).toBeVisible();
}

export async function submitLogin(page: Page, email: string, password: string) {
  await page.goto("/login");
  await page.locator("input[name=email]").fill(email);
  await page.locator("input[name=password]").fill(password);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
}

export async function logIn(page: Page, account: Account) {
  await submitLogin(page, account.email, account.password);
  await expect(page.getByRole("navigation", { name: "メニュー" })).toBeVisible();
}

/** 新規登録からログインまで済ませたセッションを返す */
export async function signUp(session: OpenSession, account: Account): Promise<Page> {
  const page = await session();
  await register(page, account);
  await openVerificationLink(page, account.email);
  await logIn(page, account);
  return page;
}

export async function writePost(page: Page, text: string) {
  await page.getByRole("textbox", { name: "いまどうしてる？" }).fill(text);
  await page.getByRole("button", { name: "投稿する" }).click();
  await expect(postRow(page, text)).toBeVisible();
}

export function postRow(page: Page, text: string): Locator {
  return page.locator("li.post", { hasText: text });
}

export async function search(page: Page, query: string) {
  await page.goto("/search");
  await page.getByRole("searchbox").fill(query);
  await page.getByRole("button", { name: "検索", exact: true }).click();
}

export async function confirmDelete(page: Page) {
  await page.getByRole("dialog").getByRole("button", { name: "削除する" }).click();
}
