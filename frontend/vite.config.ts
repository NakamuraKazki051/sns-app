/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// 開発中は Vite(5173)から API(Spring Boot の 8080)へ中継する。
// 画面と API が同じオリジンに見えるので、ログインの Cookie がそのまま使え、CORS の設定も要らない
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { "/api": "http://localhost:8080" },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    // メモリの少ない PC では並列に起動すると時間切れになるので、1つずつ順に動かす
    pool: "threads",
    maxWorkers: 1,
    fileParallelism: false,
  },
});
