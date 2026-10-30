# RaiseLine 仕様書

2026-10-30 作成。いま動いているアプリの「何がどう動くか」をまとめたもの。なぜ作るか・何を作るかは [`requirements.md`](./requirements.md)、ログインまわりの詳しい決まりは [`auth.md`](./auth.md) を参照。

## 1. 機能一覧

| ID | 機能 | 状態 |
| --- | --- | --- |
| F-01 | ユーザー登録・ログイン・ログアウト | 済 |
| F-02 | 投稿（1〜140字） | 済 |
| F-03 | 自分の投稿の削除（確認あり） | 済 |
| F-04 | 全体タイムライン（「みんな」タブ） | 済 |
| F-05 | ユーザーページ（`/@ユーザー名`） | 済 |
| F-06 | 投稿日時の相対表示 | 済 |
| F-07 | ユーザー検索 | 済 |
| F-08 | リプライ（投稿への返信） | 済 |
| F-09 | いいね | 済 |
| F-10 | フォロー・フォロー解除、フォロー中・フォロワー一覧 | 済 |
| F-11 | ホームタイムライン（「ホーム」タブ） | 済 |
| F-12 | 自己紹介（160字） | 済 |
| F-15 | 表示名・アイコンの変更、メール・パスワードの変更 | 済 |
| F-13 | リポスト | 未（フェーズ4） |
| F-14 | 画像の添付 | 未（フェーズ4） |

## 2. 共通の決まり

### 2.1 文字数

| 項目 | 上限 | 数え方 |
| --- | --- | --- |
| 投稿・リプライ | 1〜140字 | 見た目の1文字（絵文字 `👍🏽` も 1 字）。画面は `Intl.Segmenter`、サーバーは `BreakIterator` |
| 自己紹介 | 0〜160字 | 同上。空にすると消える |
| 表示名 | 1〜50字 | Java の文字数（UTF-16 の単位。絵文字は 2 以上になる）。画面も同じ数え方 |
| 検索語 | 50字まで | ― |

- 投稿・リプライ・自己紹介は前後の空白を取り、改行は残す。空白だけは送れない
- 絵文字を大量に重ねた巨大な文字列に備え、数える前に 2,000 文字（Java の文字数）でも止める

### 2.2 ページ送り（「もっと見る」）

すべて**カーソル方式**（「最後に見たものの続き」を渡す）。読んでいる間に投稿が増えても、同じものが2回出たり抜けたりしない。1ページは20件。

| 一覧 | 並び | 続きの渡し方 | 応答 |
| --- | --- | --- | --- |
| タイムライン・ユーザーの投稿 | 新しい順 | `?before=<最後の投稿 ID>` | `{ posts, hasMore }` |
| リプライ | 古い順 | `?after=<最後のリプライ ID>` | `{ replies, hasMore }` |
| ユーザー検索 | ユーザー名順 | `?after=<nextCursor>` | `{ users, nextCursor }`（続きが無ければ `null`） |
| フォロー中・フォロワー | フォローした新しい順 | `?before=<nextCursor>` | `{ users, nextCursor }` |

### 2.3 何度押しても同じ結果（冪等）

いいね・フォローは「する」「取り消す」を別の操作にしている（POST と DELETE）。連打しても、通信が重なっても、結果は1回分。最後の砦は DB の一意制約（`likes(user_id, post_id)`、`follows(follower_id, followee_id)`）。

### 2.4 連投対策

1ユーザー1分間に、投稿10件・リプライ10件まで（別々に数える）。超えたら 429「投稿が多すぎます。1分ほど待ってから…」。

### 2.5 エラー

| ステータス | 使う場面 | 画面での見せ方 |
| --- | --- | --- |
| 400 | 入力の形式・文字数、自分をフォロー | 画面上部の赤いバナーにサーバーのメッセージ |
| 401 | 未ログイン・ログイン期限切れ | ログイン画面へ移し、理由を表示 |
| 404 | 存在しない、または他人の投稿・リプライを消そうとした | 「見つかりません」の画面、またはバナー |
| 409 | メール・ユーザー名の重複 | フォームの上に赤字 |
| 413 | アイコンが 1MB を超える | バナー |
| 429 | ログイン試行・連投の回数超え | バナー |
| 0（接続失敗） | サーバーに届かない | 「サーバーに接続できません」 |

一覧の読み込みに失敗したときは、一覧の下に理由と「もう一度読み込む」ボタンを出す。

## 3. 画面

すべて最大幅 600px の中央寄せ、スマホ幅ではそのまま全幅。ヘッダー（ロゴ、ホーム、検索、プロフィール、設定、ダークモード切替、ログアウト）を上部に固定。

| 画面 | URL | 表示・操作 |
| --- | --- | --- |
| ログイン・新規登録 | `/login` | 「ログイン」「新規登録」タブ。ログイン後は開こうとした画面（`?next=`）に戻る |
| タイムライン | `/`（ホーム）、`/?tab=all`（みんな） | タブ、投稿欄（残り文字数。残り20字で黄、超えると赤で送れない。Ctrl+Enter でも送れる）、投稿一覧、もっと見る |
| 投稿の詳細 | `/posts/{id}` | 投稿、いいね、リプライ欄、リプライ一覧（古い順）。自分のリプライは削除できる。投稿を消すとタイムラインへ |
| ユーザーページ | `/@{ユーザー名}` | アイコン、表示名、@ユーザー名、自己紹介、投稿数・フォロー中・フォロワー数、フォローボタン（自分のページなら「プロフィールを編集」）、投稿一覧 |
| フォロー中・フォロワー | `/@{ユーザー名}/following`、`/followers` | タブで切替。ユーザーの一覧（アイコン、表示名、@ユーザー名、自己紹介の先頭2行） |
| ユーザー検索 | `/search?q=…` | 検索欄、結果一覧。検索語は URL に入るので戻る・再読み込みでも残る |
| 設定 | `/settings` | プロフィール（アイコン・表示名・自己紹介をまとめて保存）、メールアドレス、パスワード |
| 見つからない | その他 | 「ページが見つかりません」とタイムラインへのリンク |

### 3.1 投稿1件の表示

| 部分 | 内容 |
| --- | --- |
| 上段 | アイコン、表示名（ユーザーページへ）、@ユーザー名、日時（「たった今」「3分前」「5時間前」「10月28日」「2025年10月28日」。マウスを乗せると正確な日時。押すと詳細へ） |
| 本文 | 文字として表示（HTML は解釈しない）。改行はそのまま |
| 下段 | 💬 リプライ数（詳細へ）、♡ いいね数（押すと切替。いいね済みは ♥ でピンク）、自分の投稿なら「削除」 |

一覧では投稿の余白を押すと詳細へ移る（文字を選んでいるときは移らない）。

### 3.2 アイコン

- 画像が無ければ、表示名の頭文字を色つきの丸で出す（色はユーザー名から決まる）
- 画像の URL は `/api/users/{ユーザー名}/icon?v=<更新時刻>`。変えると URL も変わるので、古い画像が残らない
- 設定画面で選ぶと保存前にプレビュー。JPEG・PNG、1MB まで（画面とサーバーの両方で確かめる）

### 3.3 削除の確認

投稿・リプライの削除は確認画面を出す。最初は「キャンセル」にフォーカスがあり、Esc か背景のクリックで閉じる。

## 4. API

ログイン以外はすべてログインが必要（無ければ 401）。ID は JavaScript で誤差が出ないよう文字列で返す。日時は ISO 8601（UTC）。

### 4.1 認証・アカウント

[`auth.md`](./auth.md) の「4.1 API」を参照（`/api/auth/register`、`/login`、`/logout`、`/me`、`PUT /api/users/me`）。

### 4.2 投稿・タイムライン

| メソッド | パス | 送るもの | 返すもの | エラー |
| --- | --- | --- | --- | --- |
| GET | `/api/timeline?before=` | ― | `PostPage`（全員の投稿） | ― |
| GET | `/api/timeline/home?before=` | ― | `PostPage`（自分＋フォロー中の人） | ― |
| GET | `/api/posts/{id}` | ― | `Post` | 404 |
| POST | `/api/posts` | `{ body }` | 201 `Post` | 400、429 |
| DELETE | `/api/posts/{id}` | ― | 204。リプライといいねも消える | 404（無い・他人の投稿） |
| POST | `/api/posts/{id}/like` | ― | `{ liked: true, likesCount }` | 404 |
| DELETE | `/api/posts/{id}/like` | ― | `{ liked: false, likesCount }` | 404 |

`Post` = `{ id, body, createdAt, user: PublicUser, likesCount, repliesCount, likedByMe }`  
`PublicUser` = `{ id, username, displayName, iconUpdatedAt }`（メールアドレスは含まない）

### 4.3 リプライ

| メソッド | パス | 送るもの | 返すもの | エラー |
| --- | --- | --- | --- | --- |
| GET | `/api/posts/{id}/replies?after=` | ― | `{ replies: Reply[], hasMore }`（古い順） | 404（投稿が無い） |
| POST | `/api/posts/{id}/replies` | `{ body }` | 201 `Reply` | 400、404、429 |
| DELETE | `/api/replies/{id}` | ― | 204 | 404（無い・他人のリプライ。投稿の持ち主でも他人のリプライは消せない） |

`Reply` = `{ id, postId, body, createdAt, user: PublicUser }`

### 4.4 ユーザー・フォロー・検索

| メソッド | パス | 送るもの | 返すもの | エラー |
| --- | --- | --- | --- | --- |
| GET | `/api/users?q=&after=` | ― | `UserPage`。ユーザー名か表示名の一部（大文字・小文字は区別しない、`%` `_` も文字として探す）。空なら0件 | 400（51字以上） |
| GET | `/api/users/{username}` | ― | `Profile` | 404 |
| GET | `/api/users/{username}/posts?before=` | ― | `PostPage` | 404 |
| POST | `/api/users/{username}/follow` | ― | `{ following: true, followersCount }` | 400（自分）、404 |
| DELETE | `/api/users/{username}/follow` | ― | `{ following: false, followersCount }` | 404 |
| GET | `/api/users/{username}/following?before=` | ― | `UserPage` | 404 |
| GET | `/api/users/{username}/followers?before=` | ― | `UserPage` | 404 |
| GET | `/api/users/{username}/icon` | ― | 画像（`Cache-Control: max-age=1年, private`、`X-Content-Type-Options: nosniff`） | 404（アイコン無し） |

`Profile` = `{ id, username, displayName, iconUpdatedAt, bio, postsCount, followingCount, followersCount, followedByMe, createdAt }`  
`UserPage` = `{ users: { id, username, displayName, iconUpdatedAt, bio }[], nextCursor }`

### 4.5 プロフィール（本人）

| メソッド | パス | 送るもの | 返すもの | エラー |
| --- | --- | --- | --- | --- |
| PATCH | `/api/me/profile` | `{ displayName?, bio? }`（送った項目だけ変わる） | 本人の情報 | 400 |
| PUT | `/api/me/icon` | multipart の `icon`（JPEG・PNG、1MB まで。形式はファイルの先頭のバイトで判定） | 本人の情報 | 400、413 |
| DELETE | `/api/me/icon` | ― | 本人の情報（`iconUpdatedAt: null`） | ― |

## 5. データ

| テーブル | 主な列 | 制約・インデックス |
| --- | --- | --- |
| users | id（UUID）, email, password_hash, username, display_name, bio, icon_updated_at, created_at | email・username に一意制約 |
| user_icons | user_id（主キー）, content_type, data（MEDIUMBLOB）, updated_at | 1人1枚 |
| posts | id（連番）, user_id, body, created_at | (user_id, id) |
| replies | id（連番）, post_id, user_id, body, created_at | (post_id, id)。投稿を消すと DB が一緒に消す（ON DELETE CASCADE） |
| likes | id, user_id, post_id, created_at | (user_id, post_id) に一意制約、(post_id)。投稿を消すと一緒に消える |
| follows | id（連番）, follower_id, followee_id, created_at | (follower_id, followee_id) に一意制約、(followee_id, id) |

**速さの工夫**

- 投稿一覧は投稿者をまとめて読む（`@EntityGraph`）
- いいね数・リプライ数・自分がいいね済みかは、1ページ分（20件）をまとめて3回のクエリで数える。投稿ごとに数えると1ページで60回 DB に問い合わせることになる（N+1）
- アイコン画像は別テーブル。一覧には更新時刻だけを載せる

## 6. 構成と起動

| 部分 | 技術 | 場所 |
| --- | --- | --- |
| 画面 | React 19 + TypeScript + Vite 8 + React Router 8（UI ライブラリなし） | `frontend/` |
| API | Java 25 + Spring Boot 4.1 + Spring Data JPA | `backend/` |
| DB | MySQL 8.4（Docker、ポート 3307、ボリューム `mysql-data-v2`） | `docker-compose.yml` |

- **開発中**: Spring Boot（8080）と Vite（5173）を両方起動し、`http://localhost:5173` を開く。Vite が `/api` を 8080 に中継する
- **1つにまとめる**: `frontend` で `npm run build` → `backend` で `mvn package`。`frontend/dist` が jar に入り、`http://localhost:8080` で画面も API も出る。`/@alice` などの画面の URL は `SpaController` が `index.html` を返す

## 7. テストと品質確認

| 種類 | 件数・内容 | 実行 |
| --- | --- | --- |
| API のテスト | 61件（H2）。各 API の正常系とエラー、140字・160字の境界（絵文字）、ページ送りで重複・欠落なし、他人の投稿・リプライを消せない、いいねを同時に8回押しても1件、投稿削除でリプライ・いいねも消える、検索の `%` `_` | `cd backend && mvn test` |
| 画面のテスト | 14件（Vitest + Testing Library）。ログインの流れ（6件）、投稿欄の文字数（3件）、文字数・日時・戻り先の関数 | `cd frontend && npm test` |
| MySQL での通し確認 | 36項目（2026-10-30 実施、すべて OK）。画面の URL の配信、リプライ、いいねを10回同時に押して1件、フォロー、ホーム、検索、自己紹介、アイコン、削除の連鎖、ログアウト後の 401。MySQL に外部キーの CASCADE・一意制約・インデックスがあることも確認 | 起動中のアプリに API を順に呼ぶスクリプト |

**未確認**: Chrome での画面の目視確認は、PC のメモリ不足でブラウザが固まったため途中まで（ログイン画面、未ログイン時の移動、ユーザーページ、投稿の詳細とリプライの表示は確認済み）。

## 8. 決まっていないこと・今後

- リポスト（F-13）、画像の添付（F-14）
- 性能テスト（投稿10万件・ユーザー1,000人でタイムライン 300ms 以内）。ホームタイムラインは「フォロー中の人の投稿」を毎回集めるので、件数が増えたときに測る
- AWS へのデプロイ（HTTPS にして Cookie に `Secure` を付ける）
- `me` など、画面の URL と重なるユーザー名の予約（今は `/@me` のように `@` が付くので衝突しない）
