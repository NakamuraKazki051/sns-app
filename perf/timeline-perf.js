// 性能テスト（k6）: 投稿10万件・ユーザー1,000人のデータで、画面を開いたときに呼ぶ API の速さを測る
// 直接は実行せず、run.ps1 から実行する（専用 DB の用意・データ投入・後片付けをするため）。
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 5);
const DURATION = __ENV.DURATION || '1m';
const PASSWORD = 'perf-test-password';
// 目安: タイムラインの1ページが 300ms 以内（docs/plan.md の検証方法）
const BUDGET_MS = 300;

const ENDPOINTS = [
    'GET /api/timeline', 'GET /api/timeline?before= (中ほど)',
    'GET /api/timeline/home', 'GET /api/timeline/home?before=',
    'GET /api/timeline/home (フォロー0人)',
    'GET /api/users/{username}', 'GET /api/users/{username}/posts',
    'GET /api/posts/{id}/replies', 'GET /api/users?q=',
];

const thresholds = {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
};
for (const name of ENDPOINTS) {
    thresholds[`http_req_duration{name:${name}}`] = [`p(95)<${BUDGET_MS}`];
}

export const options = {
    scenarios: {
        // ふつうのユーザー（50人フォロー）が画面を行き来する
        users: { executor: 'constant-vus', vus: VUS, duration: DURATION, exec: 'browse' },
        // フォローが0人で投稿も1件だけの人のホーム。該当する投稿が少ないので、いちばん多く行を読む
        lonely: { executor: 'constant-vus', vus: 1, duration: DURATION, exec: 'lonely' },
    },
    thresholds,
    // ログインを保つため、繰り返しのたびに Cookie を消さない
    noCookiesReset: true,
    summaryTrendStats: ['avg', 'med', 'p(95)', 'max'],
};

let loggedIn = false;

function login(username) {
    if (loggedIn) {
        return;
    }
    const res = http.post(`${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: `${username}@example.com`, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /api/auth/login' } });
    if (!check(res, { 'ログインできる': (r) => r.status === 200 })) {
        throw new Error(`login failed for ${username}: ${res.status}`);
    }
    loggedIn = true;
}

function get(path, name) {
    const res = http.get(`${BASE_URL}${path}`, { tags: { name } });
    check(res, { [`${name} が 200`]: (r) => r.status === 200 });
    return res;
}

function lastPostId(res) {
    const posts = res.status === 200 ? res.json('posts') : [];
    return posts.length > 0 ? posts[posts.length - 1].id : null;
}

export function browse() {
    // VU ごとに別のユーザーでログインする（同じユーザーだとログインの回数制限にかかる）
    login(`perf_${String(__VU % 1000).padStart(4, '0')}`);
    const other = `perf_${String(Math.floor(Math.random() * 1000)).padStart(4, '0')}`;

    get('/api/timeline', 'GET /api/timeline');
    get(`/api/timeline?before=${Math.floor(Math.random() * 100000) + 1}`, 'GET /api/timeline?before= (中ほど)');

    const home = get('/api/timeline/home', 'GET /api/timeline/home');
    const before = lastPostId(home);
    if (before) {
        get(`/api/timeline/home?before=${before}`, 'GET /api/timeline/home?before=');
    }

    get(`/api/users/${other}`, 'GET /api/users/{username}');
    get(`/api/users/${other}/posts`, 'GET /api/users/{username}/posts');
    // 5件に1件の投稿にリプライがある
    get(`/api/posts/${(Math.floor(Math.random() * 20000) + 1) * 5}/replies`, 'GET /api/posts/{id}/replies');
    get(`/api/users?q=${encodeURIComponent('perf_0' + Math.floor(Math.random() * 10))}`, 'GET /api/users?q=');
}

export function lonely() {
    login('perf_lonely');
    get('/api/timeline/home', 'GET /api/timeline/home (フォロー0人)');
}
