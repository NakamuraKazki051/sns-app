import { useState, type FormEvent } from "react";
import { Navigate, useSearchParams } from "react-router";
import { errorMessage } from "../api";
import { useAuth } from "../auth";
import { DISPLAY_NAME_MAX, displayNameLength, safeNext } from "../text";

const USERNAME_PATTERN = /^[A-Za-z0-9_]{1,15}$/;

/** 画面でも同じ決まりを確かめて、送る前に分かるエラーはすぐ出す(サーバーでも必ず確かめる) */
function validate(mode: "login" | "register", form: Record<string, string>): string | null {
  if (!form.email.trim()) return "メールアドレスを入力してください";
  if (!form.password) return "パスワードを入力してください";
  if (mode === "login") return null;
  if (!USERNAME_PATTERN.test(form.username)) return "ユーザー名は半角英数字と _ で15文字以内で入力してください";
  if (!form.displayName.trim()) return "表示名を入力してください";
  if (displayNameLength(form.displayName.trim()) > DISPLAY_NAME_MAX) return "表示名は50文字以内で入力してください";
  if (form.password.length < 8 || form.password.length > 72) return "パスワードは8〜72文字で入力してください";
  return null;
}

export function LoginPage() {
  const { me, login, register, sessionExpired } = useAuth();
  const [params] = useSearchParams();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [form, setForm] = useState({ email: "", password: "", username: "", displayName: "" });
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  // ログイン済みなら(ログイン直後も)元の画面へ
  if (me) return <Navigate to={safeNext(params.get("next"))} replace />;

  const set = (name: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm({ ...form, [name]: e.target.value });

  async function submit(event: FormEvent) {
    event.preventDefault();
    const invalid = validate(mode, form);
    if (invalid) {
      setError(invalid);
      return;
    }
    setSending(true);
    setError(null);
    try {
      if (mode === "login") {
        await login(form.email.trim(), form.password);
      } else {
        await register({
          email: form.email.trim(),
          password: form.password,
          username: form.username,
          displayName: form.displayName.trim(),
        });
      }
    } catch (e) {
      setError(errorMessage(e));
      setSending(false);
    }
  }

  function switchMode(next: "login" | "register") {
    setMode(next);
    setError(null);
  }

  return (
    <main className="container">
      <h1 className="login-title">RaiseLine</h1>
      <div className="tabs" role="tablist">
        <button type="button" role="tab" aria-selected={mode === "login"} className={mode === "login" ? "tab active" : "tab"} onClick={() => switchMode("login")}>
          ログイン
        </button>
        <button type="button" role="tab" aria-selected={mode === "register"} className={mode === "register" ? "tab active" : "tab"} onClick={() => switchMode("register")}>
          新規登録
        </button>
      </div>
      {sessionExpired && !error && (
        <p className="notice">ログインの有効期限が切れました。もう一度ログインしてください</p>
      )}
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <form className="card form" onSubmit={submit} noValidate>
        {mode === "register" && (
          <>
            <label>
              ユーザー名
              <div className="input-prefix">
                <span>@</span>
                <input name="username" value={form.username} onChange={set("username")} maxLength={15} autoComplete="username" />
              </div>
              <small>半角英数字と _、15文字まで。あとから変えられません</small>
            </label>
            <label>
              表示名
              <input name="displayName" value={form.displayName} onChange={set("displayName")} autoComplete="nickname" />
            </label>
          </>
        )}
        <label>
          メールアドレス
          <input name="email" type="email" value={form.email} onChange={set("email")} autoComplete="email" />
        </label>
        <label>
          パスワード
          <input
            name="password"
            type="password"
            value={form.password}
            onChange={set("password")}
            autoComplete={mode === "login" ? "current-password" : "new-password"}
          />
          {mode === "register" && <small>8〜72文字</small>}
        </label>
        <button type="submit" className="button primary" disabled={sending}>
          {sending ? "送信中…" : mode === "login" ? "ログイン" : "登録してはじめる"}
        </button>
      </form>
    </main>
  );
}
