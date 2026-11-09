import { useEffect, useMemo, useRef, useState } from "react";
import { Link, NavLink, Outlet } from "react-router";
import { useAuth, useMe } from "../auth";
import { useUnreadNotifications } from "../notifications";
import type { NotificationsOutlet } from "../pages/NotificationsPage";

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
  const headerRef = useRef<HTMLElement>(null);
  const unread = useUnreadNotifications();
  const outletContext = useMemo<NotificationsOutlet>(() => ({ onNotificationsRead: unread.reset }), [unread.reset]);

  // メニューは幅によって2〜3行に折り返す。タブなどをヘッダーのすぐ下に固定できるよう、実際の高さを CSS に渡す(切り捨てて、隙間が空かないよう少し重ねる)
  useEffect(() => {
    const header = headerRef.current;
    if (!header || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(() => {
      document.documentElement.style.setProperty("--header-h", `${Math.floor(header.getBoundingClientRect().height)}px`);
    });
    observer.observe(header);
    return () => observer.disconnect();
  }, []);

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
      <header className="header" ref={headerRef}>
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
            <NavLink
              to="/notifications"
              className={navClass}
              aria-label={unread.count > 0 ? `通知(未読 ${unread.count}件)` : "通知"}
            >
              通知
              {unread.count > 0 && <span className="nav-badge">{unread.count > 99 ? "99+" : unread.count}</span>}
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
        <Outlet context={outletContext} />
      </main>
    </>
  );
}
