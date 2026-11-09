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
  /** 自分がこの人をブロック・ミュートしているか(F-20) */
  blocking: boolean;
  muting: boolean;
};

export type Post = {
  id: string;
  body: string;
  createdAt: string;
  user: PublicUser;
  likesCount: number;
  repliesCount: number;
  likedByMe: boolean;
  repostsCount: number;
  repostedByMe: boolean;
  /** タイムラインでリポストとして出すときの、リポストした人。元の投稿なら null */
  repostedBy: PublicUser | null;
  /** 添付した画像(1枚まで)。無ければ null */
  image: PostImage | null;
  /** 本文を最後に直した時刻(F-19)。直していなければ null */
  editedAt?: string | null;
  /** サーバーから読んだのではなく、投稿した直後に画面で一覧へ足した行(画面だけの目印) */
  addedHere?: boolean;
};

/** 投稿の画像。幅と高さで、読み込む前に枠の形を決める(読み込んだときに一覧がガタつかないように) */
export type PostImage = { url: string; width: number; height: number };

/** nextCursor は次のページを読むときに before に渡す値(続きがなければ null) */
export type PostPage = { posts: Post[]; hasMore: boolean; nextCursor: string | null };

/** 今見ている一覧より新しい、他の人の投稿の数(上限100)と、その投稿者(3人まで) */
export type NewPosts = { count: number; users: PublicUser[] };

export type Reply = {
  id: string;
  postId: string;
  body: string;
  createdAt: string;
  user: PublicUser;
};

export type ReplyPage = { replies: Reply[]; hasMore: boolean };

/** 通知1件(F-21)。post はどの投稿への反応か(フォローでは null) */
export type Notification = {
  id: string;
  type: "LIKE" | "REPLY" | "REPOST" | "FOLLOW";
  actor: PublicUser;
  post: { id: string; body: string } | null;
  createdAt: string;
  read: boolean;
};

/** DM 1通(F-23)。mine は自分が送ったもの、read は受け取った人が読んだか */
export type DirectMessage = {
  id: string;
  mine: boolean;
  body: string;
  createdAt: string;
  read: boolean;
};

/** DM の会話一覧の1件 */
export type Conversation = {
  partner: PublicUser;
  lastMessage: DirectMessage;
  unreadCount: number;
};
