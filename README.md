# RaiseLine

旧Twitter風のSNSを、学習用に作るリポジトリです。140文字の投稿に画像を1枚付けて、タイムラインで読み、リプライ・いいね・リポスト・フォローで反応できます。

技術構成は React + Spring Boot + MySQL です。

## ドキュメント

- [`docs/requirements.md`](./docs/requirements.md)：要件定義書
- [`docs/spec.md`](./docs/spec.md)：仕様書（画面・API・データ・テスト）
- [`docs/monitoring.md`](./docs/monitoring.md)：監視とログ（Datadog）
- [`docs/auth.md`](./docs/auth.md)：認証機能 定義書（バックエンド・フロントエンド）
- [`docs/plan.md`](./docs/plan.md)：計画書（実装ステップと進み具合）
- [`docs/openapi.yaml`](./docs/openapi.yaml)：API 仕様書（コードから自動で作る。下の「API 仕様書」）
- [`docs/swagger-plan.md`](./docs/swagger-plan.md)：Swagger（OpenAPI）で API 仕様書を自動生成した計画書

## 構成

| フォルダ | 中身 |
| --- | --- |
| `frontend/` | 画面（React 19 + TypeScript + Vite） |
| `backend/` | API（Java 25 + Spring Boot 4.1 + Maven） |
| `prototype/` | 画面のモック（HTML/CSS/JS、ダミーデータ）。`index.html` をブラウザで開くだけで動く |
| `e2e/` | 結合テスト（Playwright のシナリオ、実行スクリプト） |
| `.github/workflows/` | PR ごとのテストの自動実行（GitHub Actions） |
| `perf/` | 性能テスト（k6 のシナリオ、テストデータの SQL、実行スクリプト） |
| `infra/terraform/` | AWS 環境（EC2 + RDS MySQL）と、作る・デプロイする・消すスクリプト |
| `docker-compose.yml` | ローカルの MySQL 8.4（ポート 3307） |

## ローカルで動かす

新規登録の確認メールは、ローカルでは送りません。バックエンドのログに出る確認リンクを開いてください。

開発中（画面を直すとすぐ反映される）:

```powershell
docker compose up -d                    # MySQL を起動
cd backend; mvn spring-boot:run         # API（http://localhost:8080）
cd frontend; npm install; npm run dev   # 画面（http://localhost:5173 を開く）
```

1つにまとめて動かす（画面と API を 8080 で配信）:

```powershell
cd frontend; npm run build
cd ../backend; mvn package
java -jar target/sns-api-0.1.0.jar      # http://localhost:8080 を開く
```

テスト:

```powershell
cd backend; mvn test      # API（H2 を使うので MySQL は不要）
cd frontend; npm test     # 画面（Vitest）
./e2e/run.ps1             # 結合（本物の MySQL と Chrome。専用の DB を作って、終わったら消す）
./e2e/run.ps1 -SkipBuild -Grep 新着   # 名前で絞って動かす
```

PR と master への push では、この3つを GitHub Actions が自動で動かす。結合テストが失敗すると、画面の記録を Artifacts に残す。

性能テスト（ユーザー1,000人・投稿10万件。テスト用の DB を作って、終わったら消す。k6 が必要）:

```powershell
cd backend; mvn package -DskipTests; cd ..
./perf/run.ps1
```

終わったら `docker compose stop` で MySQL を止めます（データは残ります）。

## API 仕様書

API の仕様書は、コードから自動で作っています（[springdoc-openapi](https://springdoc.org/)）。

- **ブラウザで読む・試す：** バックエンドを起動して http://localhost:8080/swagger-ui.html を開きます。「認証」のログインの API を試すと Cookie が付き、ログインが要る API も試せます
- **ファイルで読む：** [`docs/openapi.yaml`](./docs/openapi.yaml)。起動しなくても読めます
- **AWS：** 外には見せないので、開けません（404）

API を変えたら、`docs/openapi.yaml` を更新します。忘れると `mvn test` が失敗します。

```powershell
cd backend
mvn test -Dtest=OpenApiDocTest -Dopenapi.update=true
```

- 説明は、コントローラーの `@Operation` と DTO の `@Schema` に書きます
- 返すエラーは `@ApiErrors({400, 404})` と書きます。ログインが要る API の 401 は自動で付きます
- ログインが要らない API には `@PublicApi` を付けます

## AWS で動かす

使うときだけ作り、使い終わったら消します（DB はスナップショットに残して、次に作るときに戻します）。詳しくは [`infra/terraform/README.md`](./infra/terraform/README.md)。

```powershell
aws login --region ap-northeast-1
cd infra/terraform
./up.ps1       # 環境を作ってデプロイ（20分ほど）。最後に URL が出る
./deploy.ps1   # コードを変えたらデプロイし直す
./down.ps1     # 使い終わったら消す
```
