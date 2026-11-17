// このファイルは自動で作っている。直接書き換えない。
// API を変えたら backend で mvn test -Dtest=OpenApiDocTest -Dopenapi.update=true を実行すると、
// docs/openapi.yaml と一緒に作り直される(OpenApiDocTest が、コードと合っているかも確かめる)

export interface AdminStatus {
  admin: boolean;
  openReports: number;
}

export interface ApiError {
  /** 画面が出し分けに使う目印。ログインで認証アプリのコードが要るときの TWO_FACTOR_REQUIRED だけ。ほかは付かない */
  code?: string;
  /** 画面にそのまま出せる日本語の説明 */
  message: string;
  /** HTTP ステータス */
  status: number;
  timestamp: string;
}

export interface BlockResponse {
  blocking: boolean;
}

export interface CodeRequest {
  code: string;
}

export interface Conversation {
  lastMessage: MessageResponse;
  partner: PublicUser;
  unreadCount: number;
}

export interface CreateRequest {
  /** 本文。140字まで(絵文字も1字と数える) */
  body?: string;
}

export interface DeleteAccountRequest {
  password: string;
}

export interface DisableRequest {
  code: string;
  password: string;
}

export interface EditRequest {
  /** 新しい本文。140字まで。画像のある投稿なら空でもよい */
  body?: string;
}

export interface FollowResponse {
  followersCount: number;
  following: boolean;
  /** 鍵アカウントへのリクエストを送って承認を待っているか(F-26) */
  requested: boolean;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ImagePostForm {
  /** 本文。140字まで。画像があれば空でもよい */
  body?: string;
  /** JPEG か PNG、5MBまで */
  image: string;
}

export interface ImageResponse {
  height: number;
  /** 画像の URL。読むにはログインが要る */
  url: string;
  width: number;
}

export interface LikeResponse {
  liked: boolean;
  likesCount: number;
}

export interface LoginRequest {
  code?: string;
  email: string;
  password: string;
}

export interface MessagePage {
  hasMore: boolean;
  /** 新しい順 */
  messages: MessageResponse[];
  /** 古いものを読むときに before に渡す値 */
  nextCursor: string | null;
}

export interface MessageResponse {
  /** 本文。画像だけのメッセージなら空 */
  body: string;
  createdAt: string;
  /** メッセージ ID(文字列) */
  id: string;
  /** 画像。無ければ null */
  image: ImageResponse | null;
  /** 自分が送ったものか */
  mine: boolean;
  /** 受け取った人が読んだか */
  read: boolean;
}

export interface MuteResponse {
  muting: boolean;
}

export interface NewPostsResponse {
  /** 新着の数。100で止まる */
  count: number;
  /** 新着の投稿者。重複なしで3人まで */
  users: PublicUser[];
}

export interface NotificationPage {
  hasMore: boolean;
  /** 次のページを読むときに before に渡す値 */
  nextCursor: string | null;
  notifications: NotificationResponse[];
}

export interface NotificationResponse {
  actor: PublicUser;
  createdAt: string;
  /** 通知 ID(文字列) */
  id: string;
  /** どの投稿への反応か。フォローとフォローのリクエストでは null */
  post: PostSummary | null;
  read: boolean;
  /** 種類。FOLLOW_REQUEST は鍵アカウントへのフォローのリクエスト */
  type: "LIKE" | "REPLY" | "REPOST" | "FOLLOW" | "FOLLOW_REQUEST";
}

export interface PageResponse {
  hasMore: boolean;
  /** 次のページを読むときに before に渡す値。続きが無ければ null */
  nextCursor: string | null;
  posts: PostResponse[];
}

export interface PostResponse {
  body: string;
  createdAt: string;
  /** 本文を最後に直した時刻。直していなければ null */
  editedAt: string | null;
  /** 投稿 ID(文字列) */
  id: string;
  /** 画像。無ければ null */
  image: ImageResponse | null;
  likedByMe: boolean;
  likesCount: number;
  repliesCount: number;
  /** リポストした人。リポストとして出すときだけ入り、それ以外は null */
  repostedBy: PublicUser | null;
  repostedByMe: boolean;
  repostsCount: number;
  user: PublicUser;
}

export interface PostSummary {
  body: string;
  /** 投稿 ID(文字列) */
  id: string;
}

export interface ProfileResponse {
  /** 自己紹介。無ければ null */
  bio: string | null;
  /** 自分がこの人をブロックしているか(F-20) */
  blocking: boolean;
  /** 自分がこの人の投稿・フォロー一覧を見られるか。鍵アカウントで、フォローしていなければ false */
  canViewPosts: boolean;
  createdAt: string;
  displayName: string;
  /** 自分がフォローのリクエストを送って、承認を待っているか(F-26) */
  followRequested: boolean;
  followedByMe: boolean;
  followersCount: number;
  followingCount: number;
  /** アイコンを変えた時刻。アイコンが無ければ null */
  iconUpdatedAt: string | null;
  id: string;
  /** 自分がこの人をミュートしているか(F-20) */
  muting: boolean;
  postsCount: number;
  /** 鍵アカウントか(F-26) */
  privateAccount: boolean;
  username: string;
}

export interface ProfileUpdateRequest {
  /** 自己紹介。160字まで(絵文字も1字と数える)。空にすると消える */
  bio?: string;
  displayName?: string;
  /** 鍵アカウントにするか(F-26)。外すと、届いているフォローのリクエストはすべて承認する */
  privateAccount?: boolean;
}

export interface PublicUser {
  displayName: string;
  /** アイコンを変えた時刻。アイコンが無ければ null */
  iconUpdatedAt: string | null;
  id: string;
  username: string;
}

export interface RecommendedUser {
  bio: string | null;
  displayName: string;
  iconUpdatedAt: string | null;
  id: string;
  /** FOLLOWED_BY_FOLLOWING なら、その人をフォローしているフォロー中の人の数 */
  mutualCount: number;
  /** おすすめの理由 */
  reason: "FOLLOWED_BY_FOLLOWING" | "POPULAR" | "NEW";
  username: string;
}

export interface RecoveryCodes {
  /** リカバリーコード。今だけ見られる(サーバーにはハッシュしか残らない) */
  recoveryCodes: string[];
}

export interface RegisterRequest {
  displayName: string;
  email: string;
  password: string;
  username: string;
}

export interface ReplyPage {
  hasMore: boolean;
  replies: ReplyResponse[];
}

export interface ReplyResponse {
  body: string;
  createdAt: string;
  id: string;
  postId: string;
  user: PublicUser;
}

export interface ReportRequest {
  /** 詳しい説明(任意、500字まで) */
  detail?: string;
  /** 通報する投稿の ID(文字列)。ユーザーの通報なら省く */
  postId?: string;
  /** 理由 */
  reason?: "SPAM" | "HARASSMENT" | "INAPPROPRIATE" | "IMPERSONATION" | "OTHER";
  /** 通報するユーザー名。投稿の通報なら省いてよい */
  username?: string;
}

export interface ReportResponse {
  createdAt: string;
  detail: string | null;
  /** 通報 ID(文字列) */
  id: string;
  /** 投稿の通報なら入る */
  post: ReportedPost | null;
  reason: "SPAM" | "HARASSMENT" | "INAPPROPRIATE" | "IMPERSONATION" | "OTHER";
  reporter: PublicUser;
  resolution: "DISMISSED" | "POST_DELETED" | null;
  resolvedAt: string | null;
  target: PublicUser;
}

export interface ReportedPost {
  /** 通報した時点の本文 */
  body: string;
  /** 投稿 ID。投稿が消されていれば null */
  id: string | null;
}

export interface RepostResponse {
  reposted: boolean;
  repostsCount: number;
}

export interface RequestCount {
  count: number;
}

export interface ResendVerificationRequest {
  email: string;
}

export interface ResetPasswordRequest {
  newPassword: string;
  token: string;
}

export interface ResolveRequest {
  /** DISMISSED(問題なし)か POST_DELETED(投稿を消す) */
  resolution?: "DISMISSED" | "POST_DELETED";
}

export interface SendRequest {
  /** 本文。500字まで(絵文字も1字と数える) */
  body?: string;
}

export interface SessionResponse {
  createdAt: string;
  current: boolean;
  device: string;
  id: string;
  lastUsedAt: string;
}

export interface Setup {
  /** 秘密の鍵(Base32)。QR コードを読めないときに認証アプリへ手で入れる */
  secret: string;
  /** 認証アプリに読ませる otpauth:// の URI。画面が QR コードにする */
  uri: string;
}

export interface Status {
  /** 有効か */
  enabled: boolean;
  /** まだ使っていないリカバリーコードの数(無効なら0) */
  recoveryCodesLeft: number;
}

export interface Trend {
  postsCount: number;
  tag: string;
}

export interface UnreadCount {
  count: number;
}

export interface UpdateRequest {
  currentPassword: string;
  /** 新しいメールアドレス。変えないなら省く */
  email?: string;
  /** 新しいパスワード。変えないなら省く */
  newPassword?: string;
}

export interface UserPage {
  /** 次のページを読むときに渡す値。続きが無ければ null */
  nextCursor: string | null;
  users: UserSummary[];
}

export interface UserResponse {
  /** 自己紹介。無ければ null */
  bio: string | null;
  createdAt: string;
  displayName: string;
  email: string;
  /** アイコンを変えた時刻。アイコンが無ければ null */
  iconUpdatedAt: string | null;
  id: string;
  /** 鍵アカウントか(F-26) */
  privateAccount: boolean;
  username: string;
}

export interface UserSummary {
  /** 自己紹介。無ければ null */
  bio: string | null;
  displayName: string;
  /** アイコンを変えた時刻。アイコンが無ければ null */
  iconUpdatedAt: string | null;
  id: string;
  username: string;
}

export interface VerifyEmailRequest {
  token: string;
}
