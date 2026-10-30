import { Route, Routes } from "react-router";
import { RequireAuth } from "./auth";
import { Layout } from "./components/Layout";
import { FollowListPage } from "./pages/FollowListPage";
import { LoginPage } from "./pages/LoginPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { PostPage } from "./pages/PostPage";
import { SearchPage } from "./pages/SearchPage";
import { SettingsPage } from "./pages/SettingsPage";
import { TimelinePage } from "./pages/TimelinePage";
import { UserPage } from "./pages/UserPage";

/**
 * 画面の一覧。/login 以外はログインが必要(RequireAuth)。
 * ユーザーページは /@ユーザー名。React Router は「@ + パラメーター」を書けないので、:handle で受けて @ を確かめる
 */
export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<TimelinePage />} />
        <Route path="search" element={<SearchPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="posts/:id" element={<PostPage />} />
        <Route path=":handle" element={<UserPage />} />
        <Route path=":handle/following" element={<FollowListPage key="following" kind="following" />} />
        <Route path=":handle/followers" element={<FollowListPage key="followers" kind="followers" />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
