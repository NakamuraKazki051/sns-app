import { api } from "./api";
import type { Post, PostPage, Reply, ReplyPage, UserPage, UserSummary } from "./types";
import type { Page } from "./usePaged";

/** URL が正しくないときなど、何も読まない一覧 */
export async function loadNothing<T>(): Promise<Page<T>> {
  return { items: [], next: null };
}

function withParam(path: string, name: string, value: string | null): string {
  if (value === null) return path;
  return `${path}${path.includes("?") ? "&" : "?"}${name}=${encodeURIComponent(value)}`;
}

/**
 * 投稿の一覧(新しい順)。続きの目印はサーバーが nextCursor で教えてくれる。
 * ホーム・ユーザーページは投稿とリポストが混ざるので、「最後の投稿の ID」では続きの位置が決まらない
 */
export function loadPosts(path: string) {
  return async (cursor: string | null): Promise<Page<Post>> => {
    const page = await api<PostPage>(withParam(path, "before", cursor));
    return { items: page.posts, next: page.nextCursor };
  };
}

/**
 * 一覧の1行の目印。同じ投稿が「元の投稿」と「○○さんのリポスト」で2回出ることがあるので、投稿 ID だけでは区別できない
 * (1人が同じ投稿をリポストできるのは1回だけなので、投稿 ID とリポストした人で決まる)
 */
export function postKey(post: Post): string {
  return post.repostedBy ? `${post.id}:${post.repostedBy.id}` : post.id;
}

/** リプライの一覧(古い順)。続きは「最後のリプライより新しいもの」 */
export function loadReplies(postId: string) {
  return async (cursor: string | null): Promise<Page<Reply>> => {
    const page = await api<ReplyPage>(withParam(`/api/posts/${postId}/replies`, "after", cursor));
    return { items: page.replies, next: page.hasMore ? page.replies[page.replies.length - 1].id : null };
  };
}

/** ユーザーの一覧。続きの目印はサーバーが nextCursor で教えてくれる(検索は after、フォロー一覧は before) */
export function loadUsers(path: string, cursorParam: "after" | "before") {
  return async (cursor: string | null): Promise<Page<UserSummary>> => {
    const page = await api<UserPage>(withParam(path, cursorParam, cursor));
    return { items: page.users, next: page.nextCursor };
  };
}
