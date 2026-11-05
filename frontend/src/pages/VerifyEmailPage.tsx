import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { api, errorMessage } from "../api";
import { useAuth } from "../auth";
import type { Me } from "../types";

/**
 * 確認メールのリンクの行き先。
 * メールソフトがリンクを先読みしても確認済みにならないよう、開いただけでは送らず、ボタンを押してもらう
 */
export function VerifyEmailPage() {
  const { me, updateMe } = useAuth();
  const [params] = useSearchParams();
  const token = params.get("token");
  const [sending, setSending] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(token ? null : "リンクが正しくありません");

  async function verify() {
    setSending(true);
    setError(null);
    try {
      await api<void>("/api/auth/verify-email", { method: "POST", body: { token } });
      // ログインしたまま変更を確認したら、画面のメールアドレスも新しくする
      if (me) updateMe(await api<Me>("/api/auth/me"));
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
      <div className="card form">
        <h2>メールアドレスの確認</h2>
        {done ? (
          <>
            <p>確認が終わりました。</p>
            <Link to={me ? "/settings" : "/login"} className="button primary">
              {me ? "設定に戻る" : "ログインする"}
            </Link>
          </>
        ) : (
          token && (
            <button type="button" className="button primary" disabled={sending} onClick={verify}>
              {sending ? "確認中…" : "確認する"}
            </button>
          )
        )}
        {!done && !token && <Link to="/login">ログイン画面へ</Link>}
      </div>
    </main>
  );
}
