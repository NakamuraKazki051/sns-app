import {
  expect,
  logIn,
  newAccount,
  openVerificationLink,
  passwordResetLink,
  postRow,
  register,
  search,
  signUp,
  submitLogin,
  test,
  totp,
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

test("アカウントを削除すると、投稿も消え、同じパスワードでログインできない", async ({ session }) => {
  const henry = newAccount("henry");
  const page = await signUp(session, henry);
  const text = uniqueText("henry の最後の投稿");
  await writePost(page, text);
  const viewer = await signUp(session, newAccount("ivy"));

  await page.goto("/settings");
  await page.locator("input[name=deletePassword]").fill(henry.password);
  await page.getByRole("button", { name: "アカウントを削除する" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "削除する" }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByText("アカウントを削除しました")).toBeVisible();

  await submitLogin(page, henry.email, henry.password);
  await expect(page.getByRole("alert")).toBeVisible();

  await viewer.goto(`/@${henry.username}`);
  await expect(viewer.getByText("ユーザーが見つかりません")).toBeVisible();
  await viewer.goto("/");
  await viewer.getByRole("tab", { name: "みんな" }).click();
  await expect(viewer.getByText(text)).toHaveCount(0);
});

test("パスワードを忘れても、メールのリンクから新しいパスワードを決めてログインできる", async ({ session }) => {
  const jane = newAccount("jane");
  const page = await signUp(session, jane);
  const otherDevice = await session(jane);
  const newPassword = "e2e-reset-password";
  await page.getByRole("button", { name: "ログアウト" }).click();

  await page.getByRole("link", { name: "パスワードを忘れた方" }).click();
  // ログイン画面にもメールアドレスの欄があるので、画面が替わってから入れる
  await expect(page.getByRole("heading", { name: "パスワードの再設定" })).toBeVisible();
  await page.locator("input[name=email]").fill(jane.email);
  await page.getByRole("button", { name: "リンクを送る" }).click();
  await expect(page.getByText(/が登録されていれば/)).toBeVisible();

  await page.goto(await passwordResetLink(jane.email));
  await page.locator("input[name=newPassword]").fill(newPassword);
  await page.locator("input[name=confirmPassword]").fill(newPassword);
  await page.getByRole("button", { name: "変更する" }).click();
  await expect(page.getByRole("heading", { name: "パスワードを変更しました" })).toBeVisible();

  // ほかの端末はログアウトされ、古いパスワードでは入れない
  await otherDevice.goto("/settings");
  await expect(otherDevice).toHaveURL(/\/login/);
  await submitLogin(page, jane.email, jane.password);
  await expect(page.getByRole("alert")).toBeVisible();
  await logIn(page, { ...jane, password: newPassword });
});

test("2段階認証を有効にすると、ログインで認証コードかリカバリーコードが要る", async ({ session }) => {
  const tina = newAccount("tina");
  const page = await signUp(session, tina);

  await page.goto("/settings");
  await page.getByRole("button", { name: "2段階認証を設定する" }).click();
  await expect(page.getByAltText("認証アプリに読み取らせる QR コード")).toBeVisible();
  const secret = (await page.locator(".two-factor-secret").textContent()) ?? "";
  await page.getByLabel(/6桁のコード/).fill(totp(secret));
  await page.getByRole("button", { name: "有効にする" }).click();
  const codes = page.getByRole("list", { name: "リカバリーコード" }).getByRole("listitem");
  await expect(codes).toHaveCount(10);
  const recovery = (await codes.first().textContent()) ?? "";
  await page.getByRole("button", { name: "保管しました" }).click();
  await expect(page.getByText("残りのリカバリーコード：10個")).toBeVisible();

  await page.getByRole("button", { name: "ログアウト" }).click();
  await submitLogin(page, tina.email, tina.password);
  await page.getByLabel("認証コード").fill("000000");
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await expect(page.getByRole("alert")).toHaveText("認証コードが正しくありません");
  // 有効にするのに使った時間枠のコードは使えないので、次の時間枠のもの(前後30秒までは通る)
  await page.getByLabel("認証コード").fill(totp(secret, 1));
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await expect(page.getByRole("navigation", { name: "メニュー" })).toBeVisible();

  await page.getByRole("button", { name: "ログアウト" }).click();
  await submitLogin(page, tina.email, tina.password);
  await page.getByLabel("認証コード").fill(recovery);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await expect(page.getByRole("navigation", { name: "メニュー" })).toBeVisible();
  await page.goto("/settings");
  await expect(page.getByText("残りのリカバリーコード：9個")).toBeVisible();
});
