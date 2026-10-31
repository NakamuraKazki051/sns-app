import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Navigate, useLocation } from "react-router";
import { api, setUnauthorizedHandler } from "./api";
import type { Me } from "./types";

export type RegisterParams = { email: string; password: string; username: string; displayName: string };

type AuthContextValue = {
  /** ログインしている本人。未ログインなら null */
  me: Me | null;
  /** 起動直後、ログイン中かどうかをサーバーに確かめている間は true */
  loading: boolean;
  /** セッションが切れてログイン画面に戻されたとき true(ログイン画面に理由を出す) */
  sessionExpired: boolean;
  /** 自分でログアウトしたとき true(ログイン画面に戻り先を付けない) */
  signedOut: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (params: RegisterParams) => Promise<void>;
  logout: () => Promise<void>;
  /** 設定画面で表示名などを変えたあと、ヘッダーなどの表示を新しくする */
  updateMe: (me: Me) => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * ログイン状態を持つ。ログインの証拠は HttpOnly の Cookie(SNS_SESSION)だけで、
 * JavaScript からは読めない。なので起動時に /api/auth/me を呼んで「ログイン中か」をサーバーに聞く
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);
  const [sessionExpired, setSessionExpired] = useState(false);
  const [signedOut, setSignedOut] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api<Me>("/api/auth/me")
      .then((user) => !cancelled && setMe(user))
      .catch(() => !cancelled && setMe(null))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, []);

  // ログイン中に API が 401 を返したら、ログアウト扱いにする(RequireAuth がログイン画面へ移す)
  const loggedIn = useRef(false);
  loggedIn.current = me !== null;
  useEffect(() => {
    setUnauthorizedHandler(() => {
      if (loggedIn.current) setSessionExpired(true);
      setMe(null);
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const user = await api<Me>("/api/auth/login", { method: "POST", body: { email, password } });
    setSessionExpired(false);
    setSignedOut(false);
    setMe(user);
  }, []);

  const register = useCallback(
    async (params: RegisterParams) => {
      await api<Me>("/api/auth/register", { method: "POST", body: params });
      // 登録はログインを兼ねないので、続けてログインする
      await login(params.email, params.password);
    },
    [login],
  );

  const logout = useCallback(async () => {
    try {
      await api<void>("/api/auth/logout", { method: "POST" });
    } finally {
      // サーバーに届かなくても、画面上はログアウトする
      setSignedOut(true);
      setMe(null);
    }
  }, []);

  const value = useMemo(
    () => ({ me, loading, sessionExpired, signedOut, login, register, logout, updateMe: setMe }),
    [me, loading, sessionExpired, signedOut, login, register, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth は AuthProvider の中で使う");
  return value;
}

/** ログインした人だけが見られる画面。未ログインならログイン画面へ移し、ログイン後に元の画面へ戻す */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { me, loading, signedOut } = useAuth();
  const location = useLocation();
  if (loading) {
    return <p className="empty">読み込み中…</p>;
  }
  if (!me && signedOut) {
    // 自分でログアウトしたら、次にログインした人はタイムラインから始める
    return <Navigate to="/login" replace />;
  }
  if (!me) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }
  return <>{children}</>;
}

/** 必ずログインしている画面の中で、本人の情報を取る */
export function useMe(): Me {
  const { me } = useAuth();
  if (!me) throw new Error("useMe は RequireAuth の中で使う");
  return me;
}
