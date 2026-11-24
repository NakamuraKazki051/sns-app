import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { refreshTokens } from "./api";
import { isRealtimeConnected, onRealtime, PING_INTERVAL_MS, startRealtime, stopRealtime, type RealtimeEvent } from "./realtime";

vi.mock("./api", async (original) => ({ ...(await original<typeof import("./api")>()), refreshTokens: vi.fn(async () => "ok") }));

/** ブラウザの WebSocket の代わり。作られたものを覚えておき、テストから開く・届く・閉じるを起こす */
class FakeSocket {
  static OPEN = 1;
  static instances: FakeSocket[] = [];
  readyState = 0;
  sent: string[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((m: { data: string }) => void) | null = null;
  onclose: ((c: { code: number }) => void) | null = null;
  constructor(readonly url: string) {
    FakeSocket.instances.push(this);
  }
  send(data: string) {
    this.sent.push(data);
  }
  close() {
    this.readyState = 3;
  }
  open() {
    this.readyState = 1;
    this.onopen?.();
  }
  receive(data: unknown) {
    this.onmessage?.({ data: JSON.stringify(data) });
  }
  drop(code = 1006) {
    this.readyState = 3;
    this.onclose?.({ code });
  }
}

const latest = () => FakeSocket.instances.at(-1)!;

describe("realtime(F-28)", () => {
  let events: RealtimeEvent[];
  let unsubscribe: () => void;

  beforeEach(() => {
    vi.useFakeTimers();
    FakeSocket.instances = [];
    vi.stubGlobal("WebSocket", FakeSocket);
    vi.mocked(refreshTokens).mockClear();
    events = [];
    unsubscribe = onRealtime((e) => events.push(e));
  });

  afterEach(() => {
    unsubscribe();
    stopRealtime();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it("同じホストの /api/ws につなぎ、届いた知らせを渡す", () => {
    startRealtime();
    expect(latest().url).toBe(`ws://${window.location.host}/api/ws`);
    latest().open();
    expect(isRealtimeConnected()).toBe(true);
    latest().receive({ type: "DM", from: "bob" });
    latest().receive({ type: "NOTIFICATION" });
    expect(events).toEqual([{ type: "DM", from: "bob" }, { type: "NOTIFICATION" }]);
  });

  it("つながっている間は ping を送り続ける", () => {
    startRealtime();
    latest().open();
    vi.advanceTimersByTime(PING_INTERVAL_MS * 2);
    expect(latest().sent).toEqual(["ping", "ping"]);
  });

  it("切れたらつなぎ直し、取りこぼしに備えて RECONNECTED を出す", async () => {
    startRealtime();
    latest().open();
    latest().drop();
    expect(FakeSocket.instances).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(1000);
    expect(FakeSocket.instances).toHaveLength(2);
    // ふつうに切れただけならトークンは作り直さない
    expect(refreshTokens).not.toHaveBeenCalled();
    latest().open();
    expect(events).toEqual([{ type: "RECONNECTED" }]);
  });

  it("トークンの期限で閉じられたら、作り直してからつなぎ直す", async () => {
    startRealtime();
    latest().open();
    latest().drop(4001);
    await vi.advanceTimersByTimeAsync(1000);
    expect(refreshTokens).toHaveBeenCalledOnce();
    expect(FakeSocket.instances).toHaveLength(2);
  });

  it("つながらないときは、待つ時間を倍々に延ばす(30秒まで)", async () => {
    startRealtime();
    latest().drop();
    await vi.advanceTimersByTimeAsync(1000);
    expect(FakeSocket.instances).toHaveLength(2);
    latest().drop();
    await vi.advanceTimersByTimeAsync(1999);
    expect(FakeSocket.instances).toHaveLength(2);
    await vi.advanceTimersByTimeAsync(1);
    expect(FakeSocket.instances).toHaveLength(3);
  });

  it("ログイン切れで作り直せなければ、つなぎ直すのをやめる", async () => {
    vi.mocked(refreshTokens).mockResolvedValue("unauthorized");
    startRealtime();
    latest().drop();
    await vi.advanceTimersByTimeAsync(60_000);
    expect(FakeSocket.instances).toHaveLength(1);
  });

  it("ログアウトしたら閉じて、つなぎ直さない", async () => {
    startRealtime();
    latest().open();
    stopRealtime();
    latest().drop(1000);
    await vi.advanceTimersByTimeAsync(60_000);
    expect(FakeSocket.instances).toHaveLength(1);
  });

  it("止めてすぐ始め直しても、古いつながりが閉じたときに2本目をつながない(StrictMode の付け直し)", async () => {
    startRealtime();
    const old = latest();
    old.open();
    stopRealtime();
    startRealtime();
    const current = latest();
    current.open();
    // 古い方の「閉じた」は、始め直したあとで届く
    old.drop(1000);
    await vi.advanceTimersByTimeAsync(60_000);
    expect(FakeSocket.instances).toHaveLength(2);
    // 新しい方の ping は止まらない
    expect(current.sent.length).toBeGreaterThan(0);
  });

  it("トークンを作り直している間に始め直されたら、古いつなぎ直しは捨てる", async () => {
    let finishRefresh: (value: "ok") => void = () => {};
    vi.mocked(refreshTokens).mockImplementationOnce(() => new Promise((resolve) => (finishRefresh = resolve)));
    startRealtime();
    latest().open();
    latest().drop(4001);
    await vi.advanceTimersByTimeAsync(1000);
    expect(refreshTokens).toHaveBeenCalledTimes(1);
    stopRealtime();
    startRealtime();
    expect(FakeSocket.instances).toHaveLength(2);
    finishRefresh("ok");
    await vi.advanceTimersByTimeAsync(60_000);
    expect(FakeSocket.instances).toHaveLength(2);
  });
});
