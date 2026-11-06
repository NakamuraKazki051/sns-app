import { defineConfig } from "@playwright/test";

// 本物のアプリ(jar)・MySQL・Chrome をつないで確かめる。アプリの起動は run.ps1 か GitHub Actions が行う
export default defineConfig({
  testDir: "./tests",
  // メモリの少ない PC でも動くよう、1つずつ順に動かす
  workers: 1,
  timeout: 120_000,
  expect: { timeout: 10_000 },
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:8082",
    // ブラウザをダウンロードせず、入っている Chrome を使う
    channel: "chrome",
    locale: "ja-JP",
    timezoneId: "Asia/Tokyo",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
});
