# RaiseLine

旧Twitter風のSNSを、学習用に作るリポジトリです。140文字の投稿を書いて、みんなの投稿をタイムラインで読めるアプリを目指します。

技術構成は React + Spring Boot + MySQL です。

## ドキュメント

- [`docs/requirements.md`](./docs/requirements.md)：要件定義書
- [`docs/plan.md`](./docs/plan.md)：計画書（実装ステップと進み具合）

## 構成

| フォルダ | 中身 |
| --- | --- |
| `prototype/` | 画面のモック（HTML/CSS/JS、ダミーデータ）。`index.html` をブラウザで開くだけで動く |
| `backend/` | API（Java 25 + Spring Boot 4.1 + Maven） |
| `docker-compose.yml` | ローカルの MySQL 8.4（ポート 3307） |

## ローカルで動かす

```powershell
docker compose up -d     # MySQL を起動
cd backend
mvn spring-boot:run      # http://localhost:8080 で API が起動
mvn test                 # テスト（H2 を使うので MySQL は不要）
```

終わったら `docker compose down` で MySQL を止めます（データは残ります）。
