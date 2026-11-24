import { useEffect, useRef } from "react";
import { refreshTokens } from "./api";

/**
 * サーバーからの知らせ(F-28)。中身は送られてこないので、受け取った画面がいつもの API で読み直す。
 * RECONNECTED は画面が作るもの(つなぎ直したとき。切れていた間の知らせを取りこぼしたかもしれないので、全部読み直す)
 */
export type RealtimeEvent = { type: "NOTIFICATION" | "DM" | "POST" | "RECONNECTED"; from?: string };

type Listener = (event: RealtimeEvent) => void;

/** CloudFront などが静かなつながりを切らないよう、この間隔で "ping" を送る */
export const PING_INTERVAL_MS = 25_000;
/** つながらないときに待つ時間の上限(1秒から倍々に延ばす) */
export const MAX_RETRY_MS = 30_000;
/** サーバーが閉じた理由: アクセストークンの期限が来た(RealtimeHandler.TOKEN_EXPIRED) */
const TOKEN_EXPIRED = 4001;

const listeners = new Set<Listener>();
let socket: WebSocket | null = null;
let running = false;
/**
 * startRealtime のたびに増やす。止めてすぐ始め直したとき(StrictMode の付け直し、ログアウト直後のログイン)、
 * 前のつながりの「閉じた」や、待っている途中のつなぎ直しが、新しい方と重ねてつながないように
 */
let generation = 0;
let attempts = 0;
let everConnected = false;
let retryTimer: ReturnType<typeof setTimeout> | undefined;
let pingTimer: ReturnType<typeof setInterval> | undefined;

function emit(event: RealtimeEvent) {
  for (const listener of [...listeners]) listener(event);
}

/** つながっているか。つながっている間は、画面が聞きに行く回数を減らしてよい */
export function isRealtimeConnected(): boolean {
  return socket !== null && socket.readyState === WebSocket.OPEN;
}

function url(): string {
  return `${window.location.protocol === "https:" ? "wss" : "ws"}://${window.location.host}/api/ws`;
}

function connect(gen: number) {
  if (!running || gen !== generation || typeof WebSocket === "undefined") return;
  let opened = false;
  const ws = new WebSocket(url());
  socket = ws;
  ws.onopen = () => {
    if (gen !== generation) return;
    opened = true;
    attempts = 0;
    clearInterval(pingTimer);
    pingTimer = setInterval(() => ws.readyState === WebSocket.OPEN && ws.send("ping"), PING_INTERVAL_MS);
    // 2回目以降は、切れていた間の知らせを取りこぼしたかもしれない
    if (everConnected) emit({ type: "RECONNECTED" });
    everConnected = true;
  };
  ws.onmessage = (message) => {
    if (gen !== generation) return;
    try {
      const event = JSON.parse(String(message.data)) as RealtimeEvent;
      if (event && typeof event.type === "string") emit(event);
    } catch {
      // 知らない形の知らせは無視する
    }
  };
  ws.onclose = (close) => {
    if (socket === ws) socket = null;
    // 止めたあとに閉じた古いつながりは、新しいつながりの ping を止めたり、つなぎ直したりしない
    if (!running || gen !== generation) return;
    clearInterval(pingTimer);
    void reconnect(gen, close.code === TOKEN_EXPIRED || !opened);
  };
}

/**
 * つなぎ直す。期限切れで閉じられた・つなげなかった(401 かもしれない)ときは、先にアクセストークンを作り直す。
 * 作り直せない(ログイン切れ)ならやめる(画面は次の API で 401 を受けてログイン画面へ移る)
 */
async function reconnect(gen: number, refreshFirst: boolean) {
  const delay = Math.min(MAX_RETRY_MS, 1000 * 2 ** attempts);
  attempts++;
  await new Promise<void>((resolve) => {
    retryTimer = setTimeout(resolve, delay);
  });
  if (!running || gen !== generation) return;
  const refreshed = refreshFirst ? await refreshTokens() : "ok";
  // トークンを作り直している間に、止められた・始め直された
  if (!running || gen !== generation) return;
  if (refreshed === "unauthorized") {
    running = false;
    return;
  }
  connect(gen);
}

/** ログインしている間つなぐ(Layout が呼ぶ) */
export function startRealtime() {
  if (running) return;
  running = true;
  generation++;
  attempts = 0;
  everConnected = false;
  connect(generation);
}

/** ログアウトしたら閉じる */
export function stopRealtime() {
  running = false;
  clearTimeout(retryTimer);
  clearInterval(pingTimer);
  socket?.close();
  socket = null;
}

/** 知らせを受け取る。返り値で受け取るのをやめる */
export function onRealtime(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** 画面の部品が知らせを受け取る。listener は毎回変わってもよい(最新のものを呼ぶ) */
export function useRealtime(listener: Listener) {
  const ref = useRef(listener);
  ref.current = listener;
  useEffect(() => onRealtime((event) => ref.current(event)), []);
}
