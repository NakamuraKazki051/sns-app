import { confirmDelete, expect, newAccount, postRow, search, signUp, test, uniqueText, writePost } from "./support";

test("投稿 → 検索 → フォロー → いいね・リポスト・リプライ → 削除まで、2人のやりとりがつながる", async ({ session }) => {
  const alice = newAccount("alice");
  const bob = newAccount("bob");
  const a = await signUp(session, alice);
  const b = await signUp(session, bob);
  const text = uniqueText("はじめての投稿");
  const replyText = "いい投稿ですね";
  let postUrl = "";

  await test.step("投稿は「みんな」に出て、フォローするまでホームには出ない", async () => {
    await writePost(a, text);
    await b.goto("/");
    await expect(b.getByText("まだ投稿がありません。")).toBeVisible();
    await b.getByRole("tab", { name: "みんな" }).click();
    await expect(postRow(b, text)).toBeVisible();
    await expect(postRow(b, text).getByRole("button", { name: "削除" })).toHaveCount(0);
  });

  await test.step("検索から相手のページを開いてフォローすると、ホームに出る", async () => {
    await search(b, alice.username);
    await b.getByRole("link", { name: alice.displayName }).click();
    await expect(b).toHaveURL(new RegExp(`/@${alice.username}$`));
    await b.getByRole("button", { name: "フォローする" }).click();
    await expect(b.getByRole("button", { name: "フォロー中" })).toBeVisible();
    await expect(b.locator(".profile-stats")).toContainText("1 フォロワー");

    await b.getByRole("link", { name: "ホーム" }).click();
    await expect(postRow(b, text)).toBeVisible();
  });

  await test.step("いいね・リポスト・リプライの結果が、相手の画面にも出る", async () => {
    const row = postRow(b, text);
    await row.getByRole("button", { name: "いいね", exact: true }).click();
    await expect(row.getByRole("button", { name: "いいねを取り消す" })).toContainText("1");
    await row.getByRole("button", { name: "リポスト", exact: true }).click();
    await expect(row.getByRole("button", { name: "リポストを取り消す" })).toContainText("1");

    await row.getByRole("link", { name: "リプライ 0件" }).click();
    await b.getByRole("textbox", { name: "リプライを書く" }).fill(replyText);
    await b.getByRole("button", { name: "リプライ", exact: true }).click();
    await expect(b.locator("li.reply", { hasText: replyText })).toBeVisible();
    postUrl = b.url();

    await a.goto(postUrl);
    const mine = a.locator("li.post:not(.reply)");
    await expect(mine.getByRole("button", { name: "いいね", exact: true })).toContainText("1");
    await expect(mine.getByRole("button", { name: "リポスト", exact: true })).toContainText("1");
    await expect(mine.getByRole("link", { name: "リプライ 1件" })).toBeVisible();
    await expect(a.locator("li.reply", { hasText: replyText })).toBeVisible();
  });

  await test.step("フォローとリポストが、プロフィールの一覧に出る", async () => {
    await a.goto(`/@${alice.username}/followers`);
    await expect(a.getByRole("link", { name: bob.displayName })).toBeVisible();
    await a.goto(`/@${bob.username}`);
    await expect(postRow(a, text)).toContainText(`${bob.displayName}さんがリポスト`);
  });

  await test.step("リプライを消すと数が減り、再読み込みしても変わらない", async () => {
    const reply = b.locator("li.reply", { hasText: replyText });
    await reply.getByRole("button", { name: "削除" }).click();
    await confirmDelete(b);
    await expect(reply).toHaveCount(0);
    await b.reload();
    await expect(b.getByRole("link", { name: "リプライ 0件" })).toBeVisible();
    await expect(b.getByText("まだリプライがありません")).toBeVisible();
  });

  await test.step("投稿を消すと、リポストも一緒に消え、URL を開いても見つからない", async () => {
    await a.goto(postUrl);
    await a.locator("li.post").getByRole("button", { name: "削除" }).click();
    await confirmDelete(a);
    await expect(a.getByText("投稿を削除しました")).toBeVisible();

    await a.goto(`/@${alice.username}`);
    await expect(a.locator(".profile-stats")).toContainText("0 件の投稿");

    await b.goto(`/@${bob.username}`);
    await expect(b.getByRole("heading", { name: bob.displayName })).toBeVisible();
    await expect(postRow(b, text)).toHaveCount(0);
    await b.goto(postUrl);
    await expect(b.getByText("投稿が見つかりません")).toBeVisible();
  });

  await test.step("フォローを外すと、その人の投稿はホームに出なくなる", async () => {
    const later = uniqueText("フォローを外したあとの投稿");
    await a.goto("/");
    await writePost(a, later);
    await b.goto(`/@${alice.username}`);
    await b.getByRole("button", { name: "フォロー中" }).click();
    await expect(b.getByRole("button", { name: "フォローする" })).toBeVisible();
    await b.goto("/");
    await expect(b.getByText("まだ投稿がありません。")).toBeVisible();
    await expect(postRow(b, later)).toHaveCount(0);
  });
});

test("相手が投稿すると新着のお知らせが出て、押すと読み込まれる", async ({ session }) => {
  const a = await signUp(session, newAccount("carl"));
  const b = await signUp(session, newAccount("dana"));
  await b.goto("/?tab=all");
  await expect(b.getByRole("tab", { name: "みんな", selected: true })).toBeVisible();
  await expect(b.locator(".composer")).toBeVisible();

  const text = uniqueText("新着の投稿");
  await writePost(a, text);

  // 30秒ごとに確かめている
  const banner = b.getByRole("button", { name: /新しい投稿 \d+件/ });
  await expect(banner).toBeVisible({ timeout: 40_000 });
  await banner.click();
  await expect(postRow(b, text)).toBeVisible();
  await expect(banner).toHaveCount(0);
});

test("画像付きの投稿が、ほかの人の画面に表示され、投稿を消すと画像も消える", async ({ session }) => {
  const a = await signUp(session, newAccount("erika"));
  const b = await signUp(session, newAccount("fred"));
  const text = uniqueText("画像付きの投稿");
  // 1x1 の PNG
  const png = Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
    "base64",
  );

  await a.getByLabel("画像を付ける").setInputFiles({ name: "e2e.png", mimeType: "image/png", buffer: png });
  await expect(a.getByRole("img", { name: "添付する画像" })).toBeVisible();
  await writePost(a, text);

  await b.goto("/?tab=all");
  const image = postRow(b, text).locator(".post-image img");
  await expect(image).toBeVisible();
  await expect.poll(() => image.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0);
  const src = await image.getAttribute("src");

  await postRow(a, text).getByRole("button", { name: "削除" }).click();
  await confirmDelete(a);
  await expect(postRow(a, text)).toHaveCount(0);

  const response = await b.request.get(src!);
  expect(response.status()).toBe(404);
});

test("自分の投稿の本文を直すと、ほかの人にも直した本文と「編集済み」が出る", async ({ session }) => {
  const kate = newAccount("kate");
  const page = await signUp(session, kate);
  const before = uniqueText("誤字のある投稿");
  const after = uniqueText("直した投稿");
  await writePost(page, before);

  await postRow(page, before).getByRole("button", { name: "編集" }).click();
  // 直している間は本文が入力欄に移るので、行ではなく画面から探す
  await page.getByRole("textbox", { name: "投稿を編集" }).fill(after);
  await page.getByRole("button", { name: "保存する" }).click();
  await expect(postRow(page, after).getByText("(編集済み)")).toBeVisible();

  const viewer = await signUp(session, newAccount("leo"));
  await viewer.getByRole("tab", { name: "みんな" }).click();
  await expect(postRow(viewer, after).getByText("(編集済み)")).toBeVisible();
  await expect(viewer.getByText(before)).toHaveCount(0);
});

test("ミュートするとタイムラインから消え、ブロックするとお互いに見えなくなる", async ({ session }) => {
  const mia = newAccount("mia");
  const nick = newAccount("nick");
  const page = await signUp(session, mia);
  const other = await signUp(session, nick);
  const text = uniqueText("nick の投稿");
  await writePost(other, text);

  await page.reload();
  await page.getByRole("tab", { name: "みんな" }).click();
  await expect(postRow(page, text)).toBeVisible();

  await page.goto(`/@${nick.username}`);
  await page.getByRole("button", { name: "ミュート", exact: true }).click();
  await expect(page.getByRole("button", { name: "ミュート中" })).toBeVisible();
  await page.goto("/");
  await page.getByRole("tab", { name: "みんな" }).click();
  await expect(page.getByText("まだ投稿がありません").or(page.locator("li.post").first())).toBeVisible();
  await expect(postRow(page, text)).toHaveCount(0);

  await page.goto(`/@${nick.username}`);
  await page.getByRole("button", { name: "ブロック", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "ブロックする" }).click();
  await expect(page.getByRole("button", { name: "ブロック中" })).toBeVisible();
  await expect(page.getByText("ブロック中のため、投稿は表示しません")).toBeVisible();

  await other.goto(`/@${mia.username}`);
  await expect(other.getByText(`ユーザーが見つかりません: @${mia.username}`)).toBeVisible();

  // 設定画面から解除できる
  await page.goto("/settings");
  await page.getByRole("button", { name: "ブロックを解除" }).click();
  await page.getByRole("button", { name: "ミュートを解除" }).click();
  await expect(page.getByText("ブロック中のユーザーはいません")).toBeVisible();
  await expect(page.getByText("ミュート中のユーザーはいません")).toBeVisible();
  await other.reload();
  await expect(other.getByRole("heading", { name: mia.displayName })).toBeVisible();
});

test("いいね・フォローされると通知の数が出て、開くと一覧に出て既読になる", async ({ session }) => {
  const olga = newAccount("olga");
  const pete = newAccount("pete");
  const page = await signUp(session, olga);
  const text = uniqueText("olga の投稿");
  await writePost(page, text);

  const other = await signUp(session, pete);
  await other.getByRole("tab", { name: "みんな" }).click();
  await postRow(other, text).getByRole("button", { name: "いいね" }).click();
  await other.goto(`/@${olga.username}`);
  await other.getByRole("button", { name: "フォローする" }).click();

  await page.reload();
  await expect(page.getByRole("link", { name: "通知(未読 2件)" })).toBeVisible();
  await page.getByRole("link", { name: /^通知/ }).click();
  await expect(page.getByText("さんがあなたをフォローしました")).toBeVisible();
  await expect(page.getByText("さんがあなたの投稿にいいねしました")).toBeVisible();
  await expect(page.getByRole("link", { name: "通知", exact: true })).toBeVisible();
});
