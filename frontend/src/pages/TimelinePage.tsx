import { useSearchParams } from "react-router";
import { api, errorMessage } from "../api";
import { Composer } from "../components/Composer";
import { PostList } from "../components/PostList";
import { useFeedback } from "../feedback";
import { loadPosts } from "../loaders";
import type { Post } from "../types";
import { usePaged } from "../usePaged";

/** タイムライン。「ホーム」(自分とフォロー中の人)と「みんな」(全員)をタブで切り替える */
export function TimelinePage() {
  const [params, setParams] = useSearchParams();
  const tab = params.get("tab") === "all" ? "all" : "home";
  const path = tab === "all" ? "/api/timeline" : "/api/timeline/home";
  const paged = usePaged<Post>(path, loadPosts(path));
  const { showError, showToast } = useFeedback();

  async function submit(text: string) {
    try {
      const post = await api<Post>("/api/posts", { method: "POST", body: { body: text } });
      // 自分の投稿はどちらのタブにも出るので、一覧の先頭に足す
      paged.setItems((list) => [post, ...list]);
      showToast("投稿しました");
    } catch (e) {
      showError(errorMessage(e));
      throw e;
    }
  }

  return (
    <>
      <div className="tabs sticky-tabs" role="tablist">
        <button type="button" role="tab" aria-selected={tab === "home"} className={tab === "home" ? "tab active" : "tab"} onClick={() => setParams({})}>
          ホーム
        </button>
        <button type="button" role="tab" aria-selected={tab === "all"} className={tab === "all" ? "tab active" : "tab"} onClick={() => setParams({ tab: "all" })}>
          みんな
        </button>
      </div>
      <Composer placeholder="いまどうしてる？" submitLabel="投稿する" onSubmit={submit} />
      <PostList
        paged={paged}
        emptyMessage={
          tab === "home"
            ? "まだ投稿がありません。「みんな」タブや検索から、フォローする人を探してみましょう"
            : "まだ投稿がありません"
        }
      />
    </>
  );
}
