import QRCode from "qrcode";
import { useEffect, useState, type FormEvent } from "react";
import { api, errorMessage } from "../api";
import { useFeedback } from "../feedback";

type Status = { enabled: boolean; recoveryCodesLeft: number };
type Setup = { secret: string; uri: string };

/** 鍵を4文字ずつ区切る(手で入れるとき読みやすいように) */
export function groupSecret(secret: string): string {
  return secret.replace(/(.{4})(?=.)/g, "$1 ");
}

/**
 * 設定画面の「2段階認証」(F-24)。
 * 無効 →「設定する」で QR コードを出す → 認証アプリのコードを入れて有効 → リカバリーコードを一度だけ見せる。
 * 有効なら、リカバリーコードの作り直しと、外す(パスワードとコードが要る)
 */
export function TwoFactorSettings() {
  const { showError, clearError, showToast } = useFeedback();
  const [status, setStatus] = useState<Status | null>(null);
  const [setup, setSetup] = useState<Setup | null>(null);
  const [qr, setQr] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api<Status>("/api/me/two-factor")
      .then((s) => !cancelled && setStatus(s))
      .catch(() => !cancelled && setStatus(null));
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!setup) {
      setQr(null);
      return;
    }
    let cancelled = false;
    QRCode.toDataURL(setup.uri, { margin: 1, width: 200 })
      .then((url) => !cancelled && setQr(url))
      .catch(() => !cancelled && setQr(null));
    return () => {
      cancelled = true;
    };
  }, [setup]);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    clearError();
    try {
      await action();
    } catch (e) {
      showError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  const start = () =>
    run(async () => {
      setSetup(await api<Setup>("/api/me/two-factor/setup", { method: "POST" }));
      setCode("");
    });

  function enable(event: FormEvent) {
    event.preventDefault();
    if (!code.trim()) {
      showError("認証コードを入力してください");
      return;
    }
    void run(async () => {
      const result = await api<{ recoveryCodes: string[] }>("/api/me/two-factor/enable", {
        method: "POST",
        body: { code: code.trim() },
      });
      setRecoveryCodes(result.recoveryCodes);
      setSetup(null);
      setCode("");
      setStatus({ enabled: true, recoveryCodesLeft: result.recoveryCodes.length });
      showToast("2段階認証を有効にしました");
    });
  }

  function regenerate(event: FormEvent) {
    event.preventDefault();
    if (!code.trim()) {
      showError("認証コードを入力してください");
      return;
    }
    void run(async () => {
      const result = await api<{ recoveryCodes: string[] }>("/api/me/two-factor/recovery-codes", {
        method: "POST",
        body: { code: code.trim() },
      });
      setRecoveryCodes(result.recoveryCodes);
      setCode("");
      setStatus({ enabled: true, recoveryCodesLeft: result.recoveryCodes.length });
    });
  }

  function disable() {
    if (!password) {
      showError("パスワードを入力してください");
      return;
    }
    if (!code.trim()) {
      showError("認証コードを入力してください");
      return;
    }
    void run(async () => {
      await api<void>("/api/me/two-factor", { method: "DELETE", body: { password, code: code.trim() } });
      setStatus({ enabled: false, recoveryCodesLeft: 0 });
      setRecoveryCodes(null);
      setPassword("");
      setCode("");
      showToast("2段階認証を外しました");
    });
  }

  if (!status) return null;

  if (recoveryCodes) {
    return (
      <section className="card form">
        <h3>2段階認証</h3>
        <p>
          リカバリーコードです。スマホをなくしたときに、認証コードの代わりに1つずつ使えます。
          <strong>この画面を閉じると二度と表示できない</strong>ので、紙に書くなどして安全な場所に保管してください。
        </p>
        <ul className="recovery-codes" aria-label="リカバリーコード">
          {recoveryCodes.map((c) => (
            <li key={c}>
              <code>{c}</code>
            </li>
          ))}
        </ul>
        <button type="button" className="button primary" onClick={() => setRecoveryCodes(null)}>
          保管しました
        </button>
      </section>
    );
  }

  if (!status.enabled) {
    return (
      <section className="card form">
        <h3>2段階認証</h3>
        {!setup ? (
          <>
            <p className="muted">
              ログインのとき、パスワードに加えて認証アプリ（Google Authenticator など）の6桁のコードを求めます。パスワードが漏れても、スマホがなければログインできません。
            </p>
            <button type="button" className="button primary" disabled={busy} onClick={start}>
              2段階認証を設定する
            </button>
          </>
        ) : (
          <form className="form" onSubmit={enable} noValidate>
            <p>1. 認証アプリでこの QR コードを読み取ってください。</p>
            {qr && <img className="two-factor-qr" src={qr} alt="認証アプリに読み取らせる QR コード" width={200} height={200} />}
            <p className="muted">
              読み取れないときは、この鍵を手で入れてください：<code className="two-factor-secret">{groupSecret(setup.secret)}</code>
            </p>
            <label>
              2. 認証アプリに出た6桁のコード
              <input
                name="twoFactorCode"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={20}
              />
            </label>
            <button type="submit" className="button primary" disabled={busy}>
              有効にする
            </button>
            <button type="button" className="button" disabled={busy} onClick={() => setSetup(null)}>
              やめる
            </button>
          </form>
        )}
      </section>
    );
  }

  return (
    <form className="card form" onSubmit={regenerate} noValidate>
      <h3>2段階認証</h3>
      <p>
        <strong>有効</strong>です。残りのリカバリーコード：{status.recoveryCodesLeft}個
      </p>
      <label>
        認証コード（またはリカバリーコード）
        <input
          name="twoFactorCode"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={20}
        />
      </label>
      <button type="submit" className="button" disabled={busy}>
        リカバリーコードを作り直す
      </button>
      <label>
        パスワード（外すときだけ）
        <input
          name="twoFactorPassword"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
        />
      </label>
      <button type="button" className="button danger" disabled={busy} onClick={disable}>
        2段階認証を外す
      </button>
    </form>
  );
}
