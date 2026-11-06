import {
  expect,
  logIn,
  newAccount,
  openVerificationLink,
  postRow,
  register,
  search,
  signUp,
  submitLogin,
  test,
  uniqueText,
  writePost,
} from "./support";

test("登録 → メールの確認 → ログインまでつながり、確認前の登録はほかの人に見えない", async ({ session }) => {
  const viewer = await signUp(session, newAccount("view"));
  const carol = newAccount("carol");
  const page = await session();

  await register(page, carol);
  await submitLogin(page, carol.email, carol.password);
  await expect(page.getByRole("heading", { name: "メールを確認してください" })).toBeVisible();

  await search(viewer, carol.username);
  await expect(viewer.getByText(`「${carol.username}」に当てはまるユーザーはいません`)).toBeVisible();
  await viewer.goto(`/@${carol.username}`);
  await expect(viewer.getByText(`ユーザーが見つかりません: @${carol.username}`)).toBeVisible();

  await openVerificationLink(page, carol.email);
  await logIn(page, carol);

  await search(viewer, carol.username);
  await expect(viewer.getByRole("link", { name: carol.displayName })).toBeVisible();
});

test("プロフィールの変更が、投稿やユーザーページにつながる", async ({ session }) => {
  const dave = newAccount("dave");
  const page = await signUp(session, dave);
  const text = uniqueText("名前を変える前の投稿");
  await writePost(page, text);

  const newName = uniqueText("新しい名前");
  await page.goto("/settings");
  await page.locator("input[name=displayName]").fill(newName);
  await page.locator("textarea[name=bio]").fill("自己紹介です");
  await page.getByRole("button", { name: "保存する" }).click();
  await expect(page.getByText("プロフィールを保存しました")).toBeVisible();

  await page.goto(`/@${dave.username}`);
  await expect(page.getByRole("heading", { name: newName })).toBeVisible();
  await expect(page.getByText("自己紹介です")).toBeVisible();
  await expect(postRow(page, text).getByRole("link", { name: newName })).toBeVisible();
});

test("メールアドレスは確認リンクを開くまで変わらず、開くと新しいアドレスでログインできる", async ({ session }) => {
  const erin = newAccount("erin");
  const page = await signUp(session, erin);
  const newEmail = `new.${erin.email}`;

  await page.goto("/settings");
  const form = page.locator("form", { has: page.getByRole("heading", { name: "メールアドレス" }) });
  await form.locator("input[name=email]").fill(newEmail);
  await form.locator("input[name=currentPassword]").fill(erin.password);
  await form.getByRole("button", { name: "変更する" }).click();
  await expect(page.getByText(`${newEmail} に確認メールを送りました`)).toBeVisible();
  await expect(form.locator("input[name=email]")).toHaveValue(erin.email);

  await openVerificationLink(page, newEmail);
  await page.goto("/");
  await page.getByRole("button", { name: "ログアウト" }).click();
  await expect(page).toHaveURL(/\/login/);

  await submitLogin(page, erin.email, erin.password);
  await expect(page.getByRole("alert")).toBeVisible();
  await logIn(page, { ...erin, email: newEmail });
});

test("パスワードを変えると、ほかの端末のログインが切れ、新しいパスワードだけで入れる", async ({ session }) => {
  const frank = newAccount("frank");
  const page = await signUp(session, frank);
  const otherDevice = await session(frank);
  const newPassword = "e2e-new-password";

  await page.goto("/settings");
  const form = page.locator("form", { has: page.getByRole("heading", { name: "パスワード" }) });
  await form.locator("input[name=currentPassword]").fill(frank.password);
  await form.locator("input[name=newPassword]").fill(newPassword);
  await form.locator("input[name=confirmPassword]").fill(newPassword);
  await form.getByRole("button", { name: "変更する" }).click();
  await expect(page.getByText("パスワードを変更しました")).toBeVisible();

  await otherDevice.goto("/settings");
  await expect(otherDevice).toHaveURL(/\/login/);

  await submitLogin(otherDevice, frank.email, frank.password);
  await expect(otherDevice.getByRole("alert")).toBeVisible();
  await logIn(otherDevice, { ...frank, password: newPassword });
});

test("ログアウトすると画面を開けず、ログインすると元の画面へ戻る", async ({ session }) => {
  const grace = newAccount("grace");
  const page = await signUp(session, grace);

  await page.getByRole("button", { name: "ログアウト" }).click();
  await expect(page).toHaveURL(/\/login/);

  await page.goto("/settings");
  await expect(page).toHaveURL(/\/login\?next=/);
  await page.locator("input[name=email]").fill(grace.email);
  await page.locator("input[name=password]").fill(grace.password);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await expect(page).toHaveURL(/\/settings$/);
  await expect(page.getByRole("heading", { name: "設定" })).toBeVisible();
});
