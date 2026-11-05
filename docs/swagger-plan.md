# Swagger（OpenAPI）API 仕様書の自動生成 計画書

2026-11-05 作成、同日に実装した。使い方は [`README.md`](../README.md) の「API 仕様書」。

実装で計画と変わったところ:

- API は 31個になった。`POST /api/posts` の2つ（JSON と画像付き）を、仕様書では1つの API の2つの形式としてまとめたため
- `springdoc.yml` を別のファイルにした。テストは `src/test/resources/application.yml` が本番の設定を隠すので、両方から読めるようにした
- エラーは `@ApiResponse` を並べず、`@ApiErrors` と `@PublicApi` を作った。ログインが要る API の 401 は自動で付く
- `operationId` をすべて手で付けた。同じ名前のメソッド（`create`、`delete` など）は自動だと連番になり、順番で変わるため
- ログインが要ると書いた API を、未ログインで全部呼んで 401 になることを確かめるテストを足した。ここで `PUT /api/me/icon` に multipart 以外を送ると 500 になる不具合が見つかり、直した

## 目的

API の仕様書をコードから自動で作り、ブラウザで読んで試せるようにする。

- **今の課題：** API の説明は `docs/spec.md` に手で書いている。コードを変えたときに、書き忘れるとずれる
- **作るもの：** ① ブラウザで読んで試せる画面（Swagger UI）② リポジトリに置く仕様書ファイル（`docs/openapi.yaml`）
- **ずれを防ぐ仕組み：** API を変えたのに `docs/openapi.yaml` を更新していなければ、テストが失敗する

## 使う道具

| 項目 | 内容 |
| --- | --- |
| ライブラリ | `org.springdoc:springdoc-openapi-starter-webmvc-ui` 3.2.0 |
| 選んだ理由 | Spring Boot のコントローラーと DTO を読んで仕様書を作る定番のライブラリ。3.x が Spring Boot 4 向け |
| 動作の確認状況 | 3.2.0 は Maven Central の最新版（2026-11-05 時点）で、Spring Boot 4.1.2 でビルドされている。RaiseLine は 4.1.1 なので、Java 25 も含めて手順1で動作を確かめる |

入れると、次の URL が使えるようになる。

| URL | 中身 |
| --- | --- |
| `/swagger-ui.html` | 仕様書の画面。その場で API を試せる |
| `/v3/api-docs` | 仕様書の JSON |
| `/v3/api-docs.yaml` | 仕様書の YAML |

## 範囲

- **対象：** `/api/` の下の API、32個（認証4、投稿11、リプライ3、リポスト2、自分の情報3、ユーザー9）
- **対象外：** 画面を返す `SpaController`（`springdoc.paths-to-match: /api/**` で外す）
- **今回はやらない：** 仕様書から TypeScript の型を自動生成すること。`frontend/src/types.ts` は手書きのまま（やるなら別の計画にする）

## 公開の方針

| 環境 | Swagger UI と `/v3/api-docs` | 理由 |
| --- | --- | --- |
| ローカル | 使える | 開発で使う |
| AWS | 使えない（404） | 外に見せる必要がない。入口を増やさない。メモリ1GBのサーバーの負担も増やさない |

`SESSION_COOKIE_SECURE` と同じやり方にする。`application.yml` の既定はローカル向けにして、AWS は `user_data.sh.tftpl` の環境変数 `API_DOCS_ENABLED=false` で切る。仕様そのものは、リポジトリの `docs/openapi.yaml` で誰でも読める。

## 自動では分からないので、手で補うところ

RaiseLine の作りが原因で、ライブラリだけでは正しい仕様書にならないところ。ここが作業の中心になる。

| 問題 | 原因 | 対応 |
| --- | --- | --- |
| ログインの要・不要が出ない | Spring Security を使わず、`CurrentUser.require()` をコードの中で呼んでいる | Cookie 認証（`SNS_SESSION`）を `@SecurityScheme` で定義して全 API に付け、登録・ログインなどログインが要らない API だけ外す |
| エラーの応答が正しく出ない | エラーは `ApiException` を投げて `GlobalExceptionHandler` で返しているので、どの API がどのエラーを返すか読み取れない。全 API に全部のエラーが並ぶ恐れがある | `springdoc.override-with-generic-response: false` にして、API ごとに 400・401・403・404・429 を書く。よく使う組み合わせは共通のアノテーションにまとめる |
| 投稿の API が2つある | `POST /api/posts` が JSON 用と画像付き（multipart）用に分かれている | 1つの API に2種類の形式として正しくまとまるかを手順1で確かめる。まとまらなければ `@Operation` で補う |
| 画像の応答の型が分からない | 投稿の画像とアイコンはバイト列を返す | `image/jpeg`・`image/png` を返すと書く |
| 制限が DTO から読めないものがある | 140字の確認はコードの中（`bodyValue`）で、絵文字も1字と数える | `@Schema(description = …)` に書く。回数制限（1分に10回）もここに書く |
| ページ送りの `before` の形式が分からない | タイムラインの目印は `投稿ID:リポストID` という独自の形式 | `@Parameter(description = …)` に書く |

説明文は日本語で書く。`@NotBlank`・`@Size` は、ライブラリが自動で仕様書に反映する。

## 実装の手順

PR は1つ。

1. **導入**：`pom.xml` にライブラリを足し、`application.yml` に設定を書く。起動して Swagger UI が開くこと、Spring Boot 4.1.1・Java 25 で動くこと、`POST /api/posts` のまとまり方を確かめる
2. **全体の情報**：`OpenApiConfig` を作る。タイトル、バージョン、Cookie 認証を定義する
3. **認証とエラー**：ログインの要・不要と、API ごとのエラーを書く
4. **説明文**：32個の API に、`@Tag`（機能ごとの分類）、`@Operation`（説明）を付ける。DTO に `@Schema` を付ける
5. **仕様書ファイル**：`OpenApiDocTest` を作る。テスト用の H2 で `/v3/api-docs.yaml` を取り、`docs/openapi.yaml` と比べて、違えば失敗する。`-Dopenapi.update=true` を付けたときは、ファイルを書き換える。DB もサーバーの起動も要らない。毎回同じ順番で出るように `springdoc.writer-with-order-by-keys: true` にする
6. **AWS で切る**：`user_data.sh.tftpl` に `API_DOCS_ENABLED: "false"` を足す
7. **文書**：`README.md`、`docs/spec.md` に、開き方と更新のしかたを書く

## 確認すること

- [ ] `mvn test` がすべて成功する。API を変えて `docs/openapi.yaml` を更新しないと、`OpenApiDocTest` が失敗する
- [ ] Chrome の Swagger UI で、32個の API が機能ごとに並ぶ（`SpaController` は出ない）
- [ ] Chrome の Swagger UI から、ログイン → 投稿 → 画像付き投稿 → 削除を試せる。ログイン前に投稿すると 401 になる
- [ ] 仕様書の内容が実際の動きと合っている（ログインの要・不要、エラー、140字）
- [ ] AWS で `/swagger-ui.html` と `/v3/api-docs` が 404 になり、アプリのメモリ使用量と起動時間が大きく変わらない
- [ ] `docs/openapi.yaml` に、パスワードなどの秘密の値が入っていない

## 気をつけること

- **ライブラリの相性：** Spring Boot 4 系は変更が大きい。手順1で動かなければ、先に進まず版を見直す
- **メモリ：** ローカルのビルドとテストは `-Xmx384m` で動かしている。テストが増えて足りなくなったら、`OpenApiDocTest` の設定を見直す
- **オフラインのビルド：** `mvn -o` はダウンロード済みのものしか使わない。最初の1回は `-o` なしでライブラリを取得する。`deploy.ps1` は `-o` を使うので、取得する前はビルドに失敗する
- **説明文の手間：** 説明は人が書くので、書かなければ古くなる。自動で守れるのは「API の形」までで、説明の中身はレビューで見る
