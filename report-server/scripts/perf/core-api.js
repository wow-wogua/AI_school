// 批44③ 核心链路压测：登录 / 通知 / 请假列表 / 报告列表 / 学生列表（教师日常高频端点）。
// 用法：BASE=http://localhost:8080 VUS=10 DUR=2m k6 run core-api.js
// 三档封装见 run.sh（smoke 2VU/30s · load 20VU/3m · soak 10VU/15m）。
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:8080';
const USER = __ENV.USER || 'litao';
const PASS = __ENV.PASS || 'aischool123';
const CLASS_ID = Number(__ENV.CLASS_ID || 1); // 班主任所带班（报告列表端点必填）
const TERM_ID = Number(__ENV.TERM_ID || 1);   // 当前学期 id（本地栈默认 1）

export const options = {
  scenarios: {
    core: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '20s', target: Number(__ENV.VUS || 10) },
        { duration: __ENV.DUR || '2m', target: Number(__ENV.VUS || 10) },
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:login}': ['p(95)<1500'],
    'http_req_duration{endpoint:read}': ['p(95)<800'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const r = http.post(BASE + '/api/auth/login', JSON.stringify({ username: USER, password: PASS }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'login' } });
  const token = r.json('data.token');
  if (!token) throw new Error('setup 登录失败：' + r.body);
  return { token };
}

function get(token, path) {
  const r = http.get(BASE + path,
    { headers: { Authorization: 'Bearer ' + token }, tags: { endpoint: 'read' } });
  check(r, { [path.split('?')[0]]: (v) => v.status === 200 && v.json('code') === 0 });
  return r;
}

export default function (data) {
  // 登录场景：每迭代 ~10% 真登录一次（全校早高峰的形态）
  if (Math.random() < 0.1) {
    const r = http.post(BASE + '/api/auth/login', JSON.stringify({ username: USER, password: PASS }),
      { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'login' } });
    check(r, { 'auth/login': (v) => v.status === 200 && v.json('code') === 0 });
  }
  get(data.token, '/api/notification/list?pageNo=1&pageSize=20');
  get(data.token, '/api/notification/unread-count');
  get(data.token, '/api/student-leave/list?scope=my');
  get(data.token, `/api/report/list?classId=${CLASS_ID}&termId=${TERM_ID}`);
  get(data.token, '/api/student/list?pageNo=1&pageSize=20');
  sleep(1 + Math.random() * 2); // 思考时间 1~3s，模拟真人节奏
}
