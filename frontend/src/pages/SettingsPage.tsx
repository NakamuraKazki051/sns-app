import { useEffect, useState, type ChangeEvent, type FormEvent } from "react";
import { api, errorMessage } from "../api";
import { useAuth, useMe } from "../auth";
import { Avatar } from "../components/Avatar";
import { useFeedback } from "../feedback";
import { BIO_MAX, countChars, DISPLAY_NAME_MAX, displayNameLength } from "../text";
import type { Me } from "../types";

const ICON_MAX_BYTES = 1024 * 1024;

export function SettingsPage() {
  return (
    <>
      <h2 className="screen-title">設定</h2>
      <ProfileForm />
      <EmailForm />
      <PasswordForm />
    </>
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
      let updated = await api<Me>("/api/me/profile", {
        method: "PATCH",
        body: { displayName: displayName.trim(), bio: bio.trim() },
      });
      if (icon instanceof File) {
        const form = new FormData();
        form.append("icon", icon);
        updated = await api<Me>("/api/me/icon", { method: "PUT", form });
      } else if (icon === null) {
        updated = await api<Me>("/api/me/icon", { method: "DELETE" });
      }
      updateMe(updated);
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
            <input type="file" accept="image/jpeg,image/png" hidden onChange={pickIcon} />
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
        <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} autoComplete="nickname" />
        <small className={nameLength > DISPLAY_NAME_MAX ? "over" : undefined}>
          {nameLength} / {DISPLAY_NAME_MAX}
        </small>
      </label>
      <label>
        自己紹介
        <textarea rows={3} value={bio} onChange={(e) => setBio(e.target.value)} />
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
      updateMe(await api<Me>("/api/users/me", { method: "PUT", body: { email: email.trim(), currentPassword: password } }));
      setPassword("");
      showToast("メールアドレスを変更しました");
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
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
      </label>
      <label>
        今のパスワード
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
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
        <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" />
      </label>
      <label>
        新しいパスワード
        <input type="password" value={next} onChange={(e) => setNext(e.target.value)} autoComplete="new-password" />
        <small>8〜72文字</small>
      </label>
      <label>
        新しいパスワード（確認）
        <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" />
      </label>
      <button type="submit" className="button primary" disabled={saving}>
        変更する
      </button>
    </form>
  );
}
