// API の応答の形。バックエンドの *Dtos.java と揃える

/** ログインしている本人(メールアドレスを含む) */
export type Me = {
  id: string;
  email: string;
  username: string;
  displayName: string;
  iconUpdatedAt: string | null;
  bio: string | null;
  createdAt: string;
};

/** 投稿の投稿者など、他の人にも見せる部分 */
export type PublicUser = {
  id: string;
  username: string;
  displayName: string;
  iconUpdatedAt: string | null;
};

/** 検索結果・フォロー一覧の1人分 */
export type UserSummary = PublicUser & { bio: string | null };

export type UserPage = { users: UserSummary[]; nextCursor: string | null };

export type Profile = PublicUser & {
  bio: string | null;
  postsCount: number;
  followingCount: number;
  followersCount: number;
  followedByMe: boolean;
  createdAt: string;
};

export type Post = {
  id: string;
  body: string;
  createdAt: string;
  user: PublicUser;
  likesCount: number;
  repliesCount: number;
  likedByMe: boolean;
};

export type PostPage = { posts: Post[]; hasMore: boolean };

export type Reply = {
  id: string;
  postId: string;
  body: string;
  createdAt: string;
  user: PublicUser;
};

export type ReplyPage = { replies: Reply[]; hasMore: boolean };
