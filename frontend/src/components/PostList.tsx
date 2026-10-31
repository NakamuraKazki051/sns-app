import { postKey } from "../loaders";
import type { Post } from "../types";
import type { Paged } from "../usePaged";
import { MoreButton } from "./MoreButton";
import { PostItem } from "./PostItem";

type Props = {
  paged: Paged<Post>;
  emptyMessage: string;
  /** 投稿を消したあと(ユーザーページの投稿数を減らすなど) */
  onDeleted?: (id: string) => void;
};

export function PostList({ paged, emptyMessage, onDeleted }: Props) {
  const { items, setItems } = paged;
  return (
    <>
      <ul className="post-list">
        {items.map((post) => (
          <PostItem
            key={postKey(post)}
            post={post}
            // 同じ投稿が元の投稿とリポストで何行かに出ていても、いいね数などは全部の行で揃える(誰のリポストかは行ごとに残す)
            onChange={(changed) =>
              setItems((list) => list.map((p) => (p.id === changed.id ? { ...changed, repostedBy: p.repostedBy } : p)))
            }
            onDeleted={(id) => {
              setItems((list) => list.filter((p) => p.id !== id));
              onDeleted?.(id);
            }}
          />
        ))}
      </ul>
      <MoreButton
        isEmpty={items.length === 0}
        hasMore={paged.hasMore}
        loading={paged.loading}
        error={paged.error}
        onMore={paged.loadMore}
        onRetry={paged.retry}
        emptyMessage={emptyMessage}
      />
    </>
  );
}
