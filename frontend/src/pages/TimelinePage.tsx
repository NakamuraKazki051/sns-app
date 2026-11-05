import { useState } from "react";
import { useSearchParams } from "react-router";
import { api, errorMessage } from "../api";
import { useMe } from "../auth";
import { Composer } from "../components/Composer";
import { NewPostsBanner } from "../components/NewPostsBanner";
import { PostList } from "../components/PostList";
import { useFeedback } from "../feedback";
import { loadPosts, postKey } from "../loaders";
import type { Post } from "../types";
import { usePaged } from "../usePaged";
import { isNewerId, latestPostId, useNewPosts } from "../useNewPosts";

/** タイムライン。「ホーム」(自分とフォロー中の人)と「みんな」(全員)をタブで切り替える */
export function TimelinePage() {
  const me = useMe();
  const [params, setParams] = useSearchParams();
  const tab = params.get("tab") === "all" ? "all" : "home";
  const path = tab === "all" ? "/api/timeline" : "/api/timeline/home";
  const paged = usePaged<Post>(path, loadPosts(path), postKey);
  const { showError, showToast } = useFeedback();

  // 新着のお知らせ。最初の読み込みが終わるまでは、何と比べるか決まらないので聞きに行かない
  const latest = paged.loading && paged.items.length === 0 ? null : latestPostId(paged.items);
  const { newPosts, clear } = useNewPosts(`${path}/new`, latest);
  // お知らせから読み込んだとき、どの ID より新しいものに色を付けるか(タブごと)
  const [highlight, setHighlight] = useState<{ tab: string; after: string } | null>(null);
  const highlightAfter = highlight?.tab === tab ? highlight.after : null;

  function showNewPosts() {
    if (latest !== null) setHighlight({ tab, after: latest });
    clear();
    window.scrollTo({ top: 0, behavior: "smooth" });
    paged.reload();
  }

  async function submit(text: string, image: Blob | null) {
    try {
      let post: Post;
      if (image) {
        const form = new FormData();
        form.append("body", text);
        form.append("image", image, image.type === "image/png" ? "image.png" : "image.jpg");
        post = await api<Post>("/api/posts", { method: "POST", form });
      } else {
        post = await api<Post>("/api/posts", { method: "POST", body: { body: text } });
      }
      // 自分の投稿はどちらのタブにも出るので、一覧の先頭に足す
      paged.setItems((list) => [{ ...post, addedHere: true }, ...list]);
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
      {newPosts && <NewPostsBanner newPosts={newPosts} onClick={showNewPosts} />}
      <Composer placeholder="いまどうしてる？" submitLabel="投稿する" allowImage onSubmit={submit} />
      <PostList
        paged={paged}
        isNew={(post) =>
          highlightAfter !== null && !post.repostedBy && post.user.id !== me.id && isNewerId(post.id, highlightAfter)
        }
        emptyMessage={
          tab === "home"
            ? "まだ投稿がありません。「みんな」タブや検索から、フォローする人を探してみましょう"
            : "まだ投稿がありません"
        }
      />
    </>
  );
}
