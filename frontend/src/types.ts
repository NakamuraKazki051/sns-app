// API の応答の形。中身は API 仕様書から自動で作った api-schema.ts(F-29)にあり、ここでは画面で使う名前を付けるだけ。
// サーバーの形が変わると api-schema.ts が作り直され、合わない画面のコードは型の確かめ(tsc)で分かる
import type * as Api from "./api-schema";

/** ログインしている本人(メールアドレスを含む) */
export type Me = Api.UserResponse;

/** 投稿の投稿者など、他の人にも見せる部分 */
export type PublicUser = Api.PublicUser;

/** 検索結果・フォロー一覧の1人分 */
export type UserSummary = Api.UserSummary;

export type UserPage = Api.UserPage;

export type Profile = Api.ProfileResponse;

export type Post = Api.PostResponse & {
  /** サーバーから読んだのではなく、投稿した直後に画面で一覧へ足した行(画面だけの目印) */
  addedHere?: boolean;
};

/** 投稿の画像。幅と高さで、読み込む前に枠の形を決める(読み込んだときに一覧がガタつかないように) */
export type PostImage = Api.ImageResponse;

/** nextCursor は次のページを読むときに before に渡す値(続きがなければ null) */
export type PostPage = Api.PageResponse;

/** 今見ている一覧より新しい、他の人の投稿の数(上限100)と、その投稿者(3人まで) */
export type NewPosts = Api.NewPostsResponse;

export type Reply = Api.ReplyResponse;

export type ReplyPage = Api.ReplyPage;

/** 通知1件(F-21)。post はどの投稿への反応か(フォローでは null) */
export type Notification = Api.NotificationResponse;

/** DM 1通(F-23)。mine は自分が送ったもの、read は受け取った人が読んだか */
export type DirectMessage = Api.MessageResponse;

/** DM の会話一覧の1件 */
export type Conversation = Api.Conversation;
