import { useEffect, useState, type ChangeEvent, type FormEvent } from "react";
import { api, errorMessage } from "../api";
import { useAuth, useMe } from "../auth";
import { Avatar } from "../components/Avatar";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { useFeedback } from "../feedback";
import { BIO_MAX, countChars, DISPLAY_NAME_MAX, displayNameLength } from "../text";
import type { Me, PublicUser } from "../types";
import { Link } from "react-router";

const ICON_MAX_BYTES = 1024 * 1024;

export function SettingsPage() {
  return (
    <>
      <h2 className="screen-title">設定</h2>
      <ProfileForm />
      <EmailForm />
      <PasswordForm />
      <RelationList kind="mutes" />
      <RelationList kind="blocks" />
      <DeleteAccountForm />
    </>
  );
}

/** ミュート中・ブロック中の人(F-20)。ここから解除できる */
function RelationList({ kind }: { kind: "mutes" | "blocks" }) {
  const { showError } = useFeedback();
  const [users, setUsers] = useState<PublicUser[] | null>(null);
  const label = kind === "mutes" ? "ミュート" : "ブロック";

  useEffect(() => {
    let cancelled = false;
    api<PublicUser[]>(`/api/me/${kind}`)
      .then((list) => !cancelled && setUsers(list ?? []))
      .catch(() => !cancelled && setUsers([]));
    return () => {
      cancelled = true;
    };
  }, [kind]);

  async function release(user: PublicUser) {
    try {
      await api(`/api/users/${user.username}/${kind === "mutes" ? "mute" : "block"}`, { method: "DELETE" });
      setUsers((list) => (list ?? []).filter((u) => u.id !== user.id));
    } catch (e) {
      showError(errorMessage(e));
    }
  }

  return (
    <section className="card form">
      <h3>{label}中のユーザー</h3>
      {users === null ? (
        <p className="muted">読み込み中…</p>
      ) : users.length === 0 ? (
        <p className="muted">{label}中のユーザーはいません</p>
      ) : (
        <ul className="relation-list">
          {users.map((user) => (
            <li key={user.id}>
              <Link to={`/@${user.username}`}>
                {user.displayName} <span className="muted">@{user.username}</span>
              </Link>
              <button type="button" className="button" onClick={() => release(user)}>
                {label}を解除
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** アカウントの削除。パスワードを入れて、確認のダイアログでもう一度押す */
function DeleteAccountForm() {
  const { deleteAccount } = useAuth();
  const { showError, showToast } = useFeedback();
  const [password, setPassword] = useState("");
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);

  function ask(event: FormEvent) {
    event.preventDefault();
    if (!password) {
      showError("パスワードを入力してください");
      return;
    }
    setConfirming(true);
  }

  async function confirm() {
    setConfirming(false);
    setDeleting(true);
    try {
      await deleteAccount(password);
      showToast("アカウントを削除しました");
    } catch (e) {
      showError(errorMessage(e));
      setDeleting(false);
    }
  }

  return (
    <form className="card form" onSubmit={ask} noValidate>
      <h3>アカウントの削除</h3>
      <p className="muted">投稿・リプライ・いいね・リポスト・フォロー・アイコンもすべて消え、元に戻せません。</p>
      <label>
        パスワード
        <input name="deletePassword" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
      </label>
      <button type="submit" className="button danger" disabled={deleting}>
        アカウントを削除する
      </button>
      {confirming && (
        <ConfirmDialog
          title="アカウントを削除しますか？"
          message="すべてのデータが消え、元に戻せません。"
          confirmLabel="削除する"
          onConfirm={confirm}
          onCancel={() => setConfirming(false)}
        />
      )}
    </form>
  );
}

/** 表示名・自己紹介・アイコン。「保存する」でまとめて送る */
function ProfileForm() {
  const me = useMe();
  const { updateMe } = useAuth();
  const { showError, clearError, showToast } = useFeedback();
  const [displayName, setDisplayName] = useState(me.displayName);
  const [bio, setBio] = useState(me.bio ?? "");
  // 保存前に選んだアイコン。undefined: 変えない / null: 外す / File: 新しい画像
  const [icon, setIcon] = useState<File | null | undefined>(undefined);
  const [preview, setPreview] = useState<string | null | undefined>(undefined);
  const [saving, setSaving] = useState(false);

  // 選んだ画像のプレビュー用 URL は、使い終わったら解放する
  useEffect(() => {
    if (!(icon instanceof File)) {
      setPreview(icon === null ? null : undefined);
      return;
    }
    const url = URL.createObjectURL(icon);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [icon]);

  function pickIcon(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    if (!["image/jpeg", "image/png"].includes(file.type)) {
      showError("アイコン画像は JPEG か PNG にしてください");
      return;
    }
    if (file.size > ICON_MAX_BYTES) {
      showError("アイコン画像は1MBまでです");
      return;
    }
    clearError();
    setIcon(file);
  }

  const nameLength = displayNameLength(displayName.trim());
  const bioLength = countChars(bio.trim());
  const invalid = nameLength === 0 || nameLength > DISPLAY_NAME_MAX || bioLength > BIO_MAX;

  async function save(event: FormEvent) {
    event.preventDefault();
    if (invalid || saving) return;
    setSaving(true);
    try {
      // 保存できた分はすぐ画面に反映する(このあとアイコンだけ失敗しても、表示名は保存済み)
      updateMe(await api<Me>("/api/me/profile", {
        method: "PATCH",
        body: { displayName: displayName.trim(), bio: bio.trim() },
      }));
      if (icon instanceof File) {
        const form = new FormData();
        form.append("icon", icon);
        updateMe(await api<Me>("/api/me/icon", { method: "PUT", form }));
      } else if (icon === null) {
        updateMe(await api<Me>("/api/me/icon", { method: "DELETE" }));
      }
      setIcon(undefined);
      showToast("プロフィールを保存しました");
    } catch (e) {
      showError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  }

  const hasIcon = icon instanceof File || (icon === undefined && me.iconUpdatedAt !== null);
  return (
    <form className="card form" onSubmit={save} noValidate>
      <h3>プロフィール</h3>
      <div className="icon-edit">
        <Avatar user={{ ...me, displayName: displayName.trim() || "?" }} large previewSrc={preview} />
        <div className="icon-buttons">
          <label className="button">
            画像を選ぶ
            <input name="icon" type="file" accept="image/jpeg,image/png" hidden onChange={pickIcon} />
          </label>
          {hasIcon && (
            <button type="button" className="link-button" onClick={() => setIcon(null)}>
              アイコンを外す
            </button>
          )}
          <small className="muted">JPEG / PNG、1MBまで</small>
        </div>
      </div>
      <label>
        表示名
        <input name="displayName" value={displayName} onChange={(e) => setDisplayName(e.target.value)} autoComplete="off" />
        <small className={nameLength > DISPLAY_NAME_MAX ? "over" : undefined}>
          {nameLength} / {DISPLAY_NAME_MAX}
        </small>
      </label>
      <label>
        自己紹介
        <textarea name="bio" rows={3} value={bio} onChange={(e) => setBio(e.target.value)} />
        <small className={bioLength > BIO_MAX ? "over" : undefined}>
          {bioLength} / {BIO_MAX}
        </small>
      </label>
      <button type="submit" className="button primary" disabled={invalid || saving}>
        {saving ? "保存中…" : "保存する"}
      </button>
    </form>
  );
}

/** メールアドレスの変更。本人確認のため今のパスワードも入れてもらう */
function EmailForm() {
  const me = useMe();
  const { updateMe } = useAuth();
  const { showError, showToast } = useFeedback();
  const [email, setEmail] = useState(me.email);
  const [password, setPassword] = useState("");
  const [saving, setSaving] = useState(false);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!email.trim() || !password) {
      showError("新しいメールアドレスと今のパスワードを入力してください");
      return;
    }
    setSaving(true);
    try {
      const requested = email.trim();
      const updated = await api<Me>("/api/users/me", { method: "PUT", body: { email: requested, currentPassword: password } });
      updateMe(updated);
      setPassword("");
      // 新しいアドレスの確認が要るときは、リンクを開くまで変わらない。欄も今のアドレスに戻す
      setEmail(updated.email);
      showToast(
        updated.email === requested.toLowerCase()
          ? "メールアドレスを変更しました"
          : `${requested} に確認メールを送りました。リンクを開くと変更されます`,
      );
    } catch (e) {
      showError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="card form" onSubmit={save} noValidate>
      <h3>メールアドレス</h3>
      <label>
        新しいメールアドレス
        <input name="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
      </label>
      <label>
        今のパスワード
        <input name="currentPassword" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
      </label>
      <button type="submit" className="button primary" disabled={saving}>
        変更する
      </button>
    </form>
  );
}

/** パスワードの変更。変えると、ほかの端末のログインは切れる */
function PasswordForm() {
  const { updateMe } = useAuth();
  const { showError, showToast } = useFeedback();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [saving, setSaving] = useState(false);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!current || !next) {
      showError("今のパスワードと新しいパスワードを入力してください");
      return;
    }
    if (next.length < 8 || next.length > 72) {
      showError("パスワードは8〜72文字で入力してください");
      return;
    }
    if (next !== confirm) {
      showError("新しいパスワードが確認用と一致しません");
      return;
    }
    setSaving(true);
    try {
      updateMe(await api<Me>("/api/users/me", { method: "PUT", body: { currentPassword: current, newPassword: next } }));
      setCurrent("");
      setNext("");
      setConfirm("");
      showToast("パスワードを変更しました。ほかの端末ではログインし直しになります");
    } catch (e) {
      showError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="card form" onSubmit={save} noValidate>
      <h3>パスワード</h3>
      <label>
        今のパスワード
        <input name="currentPassword" type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" />
      </label>
      <label>
        新しいパスワード
        <input name="newPassword" type="password" value={next} onChange={(e) => setNext(e.target.value)} autoComplete="new-password" />
        <small>8〜72文字</small>
      </label>
      <label>
        新しいパスワード（確認）
        <input name="confirmPassword" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" />
      </label>
      <button type="submit" className="button primary" disabled={saving}>
        変更する
      </button>
    </form>
  );
}
