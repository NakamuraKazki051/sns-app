import { useParams } from "react-router";
import { PostList } from "../components/PostList";
import { loadPosts, postKey } from "../loaders";
import type { Post } from "../types";
import { usePaged } from "../usePaged";

/** ハッシュタグの付いた投稿(/hashtag/タグ、F-22) */
export function HashtagPage() {
  const tag = (useParams().tag ?? "").toLowerCase();
  const path = `/api/hashtags/${encodeURIComponent(tag)}/posts`;
  const paged = usePaged<Post>(path, loadPosts(path), postKey);
  return (
    <>
      <h2 className="screen-title">#{tag}</h2>
      <PostList paged={paged} emptyMessage={`#${tag} の付いた投稿はまだありません`} />
    </>
  );
}
