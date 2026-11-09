import { useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router";
import { api, errorMessage } from "../api";

/**
 * パスワードの再設定(F-18)。ログインしていない人が使う2つの画面。
 * 1. /forgot-password  メールアドレスを入れて、再設定のリンクを送ってもらう
 * 2. /reset-password?token=…  メールのリンクの行き先。新しいパスワードを決める
 */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!email.trim()) {
      setError("メールアドレスを入力してください");
      return;
    }
    setSending(true);
    setError(null);
    try {
      await api<void>("/api/auth/forgot-password", { method: "POST", body: { email: email.trim() } });
      setSent(true);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSending(false);
    }
  }

  return (
    <main className="container">
      <h1 className="login-title">RaiseLine</h1>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {sent ? (
        <div className="card form">
          <h2>メールを確認してください</h2>
          {/* 登録の有無が分からないよう、送らなかったときも同じ文にする */}
          <p>
            {email.trim()} が登録されていれば、パスワードを再設定するリンクを送りました。リンクの有効期限は1時間です。
          </p>
          <Link to="/login">ログイン画面へ</Link>
        </div>
      ) : (
        <form className="card form" onSubmit={submit} noValidate>
          <h2>パスワードの再設定</h2>
          <p className="muted">登録したメールアドレスに、新しいパスワードを決めるリンクを送ります。</p>
          <label>
            メールアドレス
            <input name="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
          </label>
          <button type="submit" className="button primary" disabled={sending}>
            {sending ? "送信中…" : "リンクを送る"}
          </button>
          <Link to="/login">ログイン画面へ戻る</Link>
        </form>
      )}
    </main>
  );
}

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get("token");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [sending, setSending] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(token ? null : "リンクが正しくありません");

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (password.length < 8 || password.length > 72) {
      setError("パスワードは8〜72文字で入力してください");
      return;
    }
    if (password !== confirm) {
      setError("新しいパスワードが確認用と一致しません");
      return;
    }
    setSending(true);
    setError(null);
    try {
      await api<void>("/api/auth/reset-password", { method: "POST", body: { token, newPassword: password } });
      setDone(true);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSending(false);
    }
  }

  return (
    <main className="container">
      <h1 className="login-title">RaiseLine</h1>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {done ? (
        <div className="card form">
          <h2>パスワードを変更しました</h2>
          <p>新しいパスワードでログインしてください。ほかの端末もログアウトしました。</p>
          <Link to="/login" className="button primary">
            ログインする
          </Link>
        </div>
      ) : token ? (
        <form className="card form" onSubmit={submit} noValidate>
          <h2>新しいパスワード</h2>
          <label>
            新しいパスワード
            <input name="newPassword" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" />
            <small>8〜72文字</small>
          </label>
          <label>
            新しいパスワード（確認）
            <input name="confirmPassword" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" />
          </label>
          <button type="submit" className="button primary" disabled={sending}>
            {sending ? "送信中…" : "変更する"}
          </button>
        </form>
      ) : (
        <div className="card form">
          <Link to="/forgot-password">再設定のメールを送り直す</Link>
        </div>
      )}
    </main>
  );
}
