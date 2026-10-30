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

/** 投稿の一覧(新しい順)。続きは「最後の投稿より古いもの」 */
export function loadPosts(path: string) {
  return async (cursor: string | null): Promise<Page<Post>> => {
    const page = await api<PostPage>(withParam(path, "before", cursor));
    return { items: page.posts, next: page.hasMore ? page.posts[page.posts.length - 1].id : null };
  };
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
