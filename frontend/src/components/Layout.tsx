import { useState } from "react";
import { Link, NavLink, Outlet } from "react-router";
import { useAuth, useMe } from "../auth";

type Theme = "light" | "dark";

/** テーマ(ライト/ダーク)。選んだものはこの端末に覚えておく */
export function initialTheme(): Theme {
  const saved = localStorage.getItem("theme");
  if (saved === "light" || saved === "dark") return saved;
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

/** ログイン後の画面の共通部分: 上部に固定するヘッダーと、各画面 */
export function Layout() {
  const me = useMe();
  const { logout } = useAuth();
  const [theme, setTheme] = useState<Theme>(() => document.documentElement.dataset.theme as Theme);

  function toggleTheme() {
    const next = theme === "dark" ? "light" : "dark";
    document.documentElement.dataset.theme = next;
    localStorage.setItem("theme", next);
    setTheme(next);
  }

  // ログアウトすると RequireAuth が /login へ移す(戻り先は付けない)
  async function onLogout() {
    await logout();
  }

  const navClass = ({ isActive }: { isActive: boolean }) => (isActive ? "active" : undefined);
  return (
    <>
      <header className="header">
        <div className="header-inner">
          <Link to="/" className="logo">
            RaiseLine
          </Link>
          <nav className="header-nav" aria-label="メニュー">
            <NavLink to="/" end className={navClass}>
              ホーム
            </NavLink>
            <NavLink to="/search" className={navClass}>
              検索
            </NavLink>
            <NavLink to={`/@${me.username}`} className={navClass}>
              プロフィール
            </NavLink>
            <NavLink to="/settings" className={navClass}>
              設定
            </NavLink>
            <button
              type="button"
              className="link-button"
              aria-label={theme === "dark" ? "ライトモードにする" : "ダークモードにする"}
              onClick={toggleTheme}
            >
              {theme === "dark" ? "☀️" : "🌙"}
            </button>
            <button type="button" className="link-button" onClick={onLogout}>
              ログアウト
            </button>
          </nav>
        </div>
      </header>
      <main className="container">
        <Outlet />
      </main>
    </>
  );
}
