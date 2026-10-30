// ===== ダミーデータ =====
const ME = "kazki";
const PAGE_SIZE = 20;
const MAX_LENGTH = 140;

const users = {
  kazki: { displayName: "かずき", color: "#1d9bf0" },
  hanako: { displayName: "はなこ", color: "#f91880" },
  taro: { displayName: "Taro", color: "#00ba7c" },
  java_neko: { displayName: "Javaねこ🐱", color: "#7856ff" },
};

const sampleTexts = [
  "Spring Boot のテストが全部通った！",
  "今日は React の useState を勉強した",
  "コーヒー飲みながらコード書いてる☕",
  "MySQL のインデックス、ちゃんと理解すると面白い",
  "140字って意外と書ける",
  "Docker 起動するの忘れてた😇",
  "おはようございます",
  "CSS の flexbox と grid の使い分けがやっと分かってきた",
  "バグが直らない…",
  "直った！原因はタイポだった",
];

// 新しい順。id が大きいほど新しい
let nextId = 1;
const posts = [];
const now = Date.now();
const userNames = Object.keys(users);
for (let i = 0; i < 45; i++) {
  posts.unshift({
    id: nextId++,
    username: userNames[i % userNames.length],
    text: sampleTexts[i % sampleTexts.length],
    // 古いものほど過去に。分 → 時間 → 日 の表示が全部見られるように間隔を広げる
    createdAt: now - (45 - i) ** 2.6 * 60 * 1000,
  });
}

// ===== 便利関数 =====
const $ = (id) => document.getElementById(id);

// 見た目の1文字で数える（絵文字も1字）
const segmenter = new Intl.Segmenter("ja", { granularity: "grapheme" });
const countChars = (text) => [...segmenter.segment(text)].length;

function relativeTime(time) {
  const diff = (Date.now() - time) / 1000;
  if (diff < 60) return "たった今";
  if (diff < 3600) return `${Math.floor(diff / 60)}分前`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}時間前`;
  const d = new Date(time);
  const sameYear = d.getFullYear() === new Date().getFullYear();
  return `${sameYear ? "" : d.getFullYear() + "年"}${d.getMonth() + 1}月${d.getDate()}日`;
}

function avatar(username, large = false) {
  const el = document.createElement("div");
  el.className = large ? "avatar large" : "avatar";
  fillAvatar(el, users[username]);
  return el;
}

// アイコン画像があれば画像、なければ表示名の頭文字
function fillAvatar(el, { displayName, color, icon }) {
  el.style.background = color;
  if (icon) {
    const img = document.createElement("img");
    img.src = icon;
    img.alt = "";
    el.replaceChildren(img);
  } else {
    el.textContent = [...displayName][0] ?? "?";
  }
}

function showError(message) {
  $("error-text").textContent = message;
  $("error-banner").hidden = false;
}
function hideError() {
  $("error-banner").hidden = true;
}

let toastTimer;
function showToast(message) {
  $("toast").textContent = message;
  $("toast").hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => ($("toast").hidden = true), 2000);
}

// ===== 投稿1件の表示 =====
function renderPost(post) {
  const li = document.createElement("li");
  li.className = "post";
  li.append(avatar(post.username));

  const body = document.createElement("div");
  body.className = "post-body";

  const meta = document.createElement("div");
  meta.className = "post-meta";
  const name = document.createElement("a");
  name.className = "name";
  name.href = `#/@${post.username}`;
  name.textContent = users[post.username].displayName;
  const sub = document.createElement("span");
  sub.className = "sub";
  sub.textContent = `@${post.username} · ${relativeTime(post.createdAt)}`;
  sub.title = new Date(post.createdAt).toLocaleString("ja-JP");
  meta.append(name, sub);

  // 自分の投稿だけ削除できる
  if (post.username === ME) {
    const del = document.createElement("button");
    del.type = "button";
    del.className = "link-button post-delete";
    del.textContent = "削除";
    del.addEventListener("click", () => openDeleteModal(post.id));
    meta.append(del);
  }

  const text = document.createElement("p");
  text.className = "post-text";
  text.textContent = post.text;

  body.append(meta, text);
  li.append(body);
  return li;
}

// ===== タイムライン（もっと見る） =====
let shownCount = PAGE_SIZE;

function renderTimeline() {
  const list = $("timeline-list");
  list.replaceChildren(...posts.slice(0, shownCount).map(renderPost));
  $("timeline-more").hidden = shownCount >= posts.length;
}

$("timeline-more").addEventListener("click", () => {
  shownCount += PAGE_SIZE;
  renderTimeline();
});

// ===== 投稿欄 =====
function updateCounter() {
  const text = $("composer-text").value;
  const rest = MAX_LENGTH - countChars(text);
  const counter = $("composer-counter");
  counter.textContent = rest;
  counter.className = "counter" + (rest < 0 ? " over" : rest <= 20 ? " warn" : "");
  $("composer-submit").disabled = text.trim() === "" || rest < 0;
}

$("composer-text").addEventListener("input", updateCounter);

$("composer").addEventListener("submit", (e) => {
  e.preventDefault();
  const text = $("composer-text").value.trim();
  if (text === "" || countChars(text) > MAX_LENGTH) return;
  posts.unshift({ id: nextId++, username: ME, text, createdAt: Date.now() });
  shownCount++;
  $("composer-text").value = "";
  updateCounter();
  renderTimeline();
  showToast("投稿しました");
});

// ===== 削除の確認 =====
let deletingId = null;

function openDeleteModal(id) {
  deletingId = id;
  $("delete-modal").hidden = false;
  $("delete-cancel").focus();
}
function closeDeleteModal() {
  deletingId = null;
  $("delete-modal").hidden = true;
}

$("delete-cancel").addEventListener("click", closeDeleteModal);
$("delete-modal").addEventListener("click", (e) => {
  if (e.target === $("delete-modal")) closeDeleteModal();
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && !$("delete-modal").hidden) closeDeleteModal();
});
$("delete-ok").addEventListener("click", () => {
  const index = posts.findIndex((p) => p.id === deletingId);
  if (index >= 0) posts.splice(index, 1);
  closeDeleteModal();
  route();
  showToast("削除しました");
});

// ===== ユーザーページ =====
function renderUser(username) {
  const user = users[username];
  $("profile-avatar").replaceWith(Object.assign(avatar(username, true), { id: "profile-avatar" }));
  $("profile-name").textContent = user.displayName;
  $("profile-username").textContent = `@${username}`;
  const own = posts.filter((p) => p.username === username);
  $("profile-count").textContent = own.length;
  $("user-list").replaceChildren(...own.map(renderPost));
  $("user-empty").hidden = own.length > 0;
}

// ===== ログイン / 新規登録 =====
let authMode = "login";

document.querySelectorAll("[data-auth-tab]").forEach((tab) => {
  tab.addEventListener("click", () => {
    authMode = tab.dataset.authTab;
    document.querySelectorAll("[data-auth-tab]").forEach((t) => t.classList.toggle("active", t === tab));
    document.querySelector(".register-only").hidden = authMode !== "register";
    $("auth-submit").textContent = authMode === "login" ? "ログイン" : "登録する";
    hideError();
  });
});

$("auth-form").addEventListener("submit", (e) => {
  e.preventDefault();
  const form = e.target.elements;
  if (authMode === "register") {
    if (!/^[A-Za-z0-9_]{1,15}$/.test(form.username.value)) {
      return showError("ユーザー名は半角英数字と _ で1〜15字にしてください");
    }
    if (users[form.username.value.toLowerCase()]) {
      return showError("そのユーザー名はすでに使われています");
    }
    if (form.displayName.value.trim() === "") return showError("表示名を入力してください");
  }
  if (form.email.value.trim() === "" || form.password.value === "") {
    return showError("メールアドレスとパスワードを入力してください");
  }
  if (authMode === "register" && form.password.value.length < 8) {
    return showError("パスワードは8文字以上にしてください");
  }
  loggedIn = true;
  location.hash = "#/";
  route();
});

$("logout").addEventListener("click", () => {
  loggedIn = false;
  location.hash = "#/login";
  route();
});

// ===== 設定: プロフィール（表示名・アイコン） =====
const MAX_ICON_BYTES = 1024 * 1024;
let draftIcon = null; // 保存前に選んだアイコン（data URL）

function renderProfileForm() {
  draftIcon = users[ME].icon ?? null;
  $("profile-form").elements.displayName.value = users[ME].displayName;
  $("icon-input").value = "";
  updateProfilePreview();
}

function updateProfilePreview() {
  const name = $("profile-form").elements.displayName.value;
  $("name-counter").textContent = countChars(name);
  fillAvatar($("icon-preview"), { displayName: name || "?", color: users[ME].color, icon: draftIcon });
}

$("profile-form").elements.displayName.addEventListener("input", updateProfilePreview);

$("icon-input").addEventListener("change", (e) => {
  const file = e.target.files[0];
  if (!file) return;
  if (!["image/jpeg", "image/png"].includes(file.type)) {
    e.target.value = "";
    return showError("アイコンは JPEG か PNG の画像にしてください");
  }
  if (file.size > MAX_ICON_BYTES) {
    e.target.value = "";
    return showError("アイコンは1MBまでの画像にしてください");
  }
  hideError();
  const reader = new FileReader();
  reader.onload = () => {
    draftIcon = reader.result;
    updateProfilePreview();
  };
  reader.readAsDataURL(file);
});

$("icon-reset").addEventListener("click", () => {
  draftIcon = null;
  $("icon-input").value = "";
  updateProfilePreview();
});

$("profile-form").addEventListener("submit", (e) => {
  e.preventDefault();
  const name = e.target.elements.displayName.value.trim();
  if (name === "") return showError("表示名を入力してください");
  if (countChars(name) > 50) return showError("表示名は50字までにしてください");
  hideError();
  users[ME].displayName = name;
  users[ME].icon = draftIcon;
  showToast("プロフィールを保存しました");
});

// ===== 設定: メール・パスワード =====
$("email-form").addEventListener("submit", (e) => {
  e.preventDefault();
  if (!e.target.elements.email.value.includes("@")) return showError("メールアドレスの形式が正しくありません");
  hideError();
  showToast("メールアドレスを変更しました");
});

$("password-form").addEventListener("submit", (e) => {
  e.preventDefault();
  const { current, next } = e.target.elements;
  if (current.value === "") return showError("今のパスワードを入力してください");
  if (next.value.length < 8) return showError("新しいパスワードは8文字以上にしてください");
  hideError();
  e.target.reset();
  showToast("パスワードを変更しました");
});

// ===== ダークモード =====
function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  $("theme-toggle").textContent = theme === "dark" ? "☀️" : "🌙";
  try { localStorage.setItem("theme", theme); } catch {}
}
$("theme-toggle").addEventListener("click", () => {
  applyTheme(document.documentElement.dataset.theme === "dark" ? "light" : "dark");
});
let savedTheme = null;
try { savedTheme = localStorage.getItem("theme"); } catch {}
applyTheme(savedTheme ?? (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"));

// ===== 画面遷移（#/ のハッシュで切り替え） =====
let loggedIn = false;

function showScreen(name, nav) {
  document.querySelectorAll(".screen").forEach((s) => (s.hidden = s.id !== `screen-${name}`));
  document.querySelectorAll("[data-nav]").forEach((a) => a.classList.toggle("active", a.dataset.nav === nav));
  $("header").hidden = name === "login";
}

function route() {
  hideError();
  const path = location.hash.slice(1) || "/";

  if (!loggedIn) return showScreen("login");
  if (path === "/login") {
    location.hash = "#/";
    return;
  }
  if (path === "/") {
    renderTimeline();
    return showScreen("timeline", "timeline");
  }
  if (path === "/settings") {
    renderProfileForm();
    return showScreen("settings", "settings");
  }
  if (path.startsWith("/@")) {
    const username = path.slice(2).toLowerCase();
    if (!users[username]) return showScreen("notfound");
    renderUser(username);
    return showScreen("user", username === ME ? "me" : null);
  }
  showScreen("notfound");
}

window.addEventListener("hashchange", () => {
  route();
  window.scrollTo(0, 0);
});
updateCounter();
route();
