// 批44③ 报告生成重任务压测：单生 TERM 报告（渲染队列+PDF 渲染器+MinIO 写入，系统最重链路）。
// 刻意小并发（默认 2 VU）：验证队列不丢任务、内存不爆、渲染 worker 稳定消费。
// 产物清理：压测后按 create_time 删 t_report/t_report_task 新增行（run.sh 不自动删，跑前看 README）。
// 用法：BASE=http://localhost:8080 VUS=2 DUR=2m STUDENT_ID=1 TERM_ID=2 k6 run report-gen.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:8080';
const USER = __ENV.USER || 'litao';
const PASS = __ENV.PASS || 'aischool123';
const STUDENT_ID = Number(__ENV.STUDENT_ID || 1);
const TERM_ID = Number(__ENV.TERM_ID || 2);

export const options = {
  scenarios: {
    gen: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '10s', target: Number(__ENV.VUS || 2) },
        { duration: __ENV.DUR || '2m', target: Number(__ENV.VUS || 2) },
        { duration: '10s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],      // 只压提交侧；渲染异步
    'http_req_duration': ['p(95)<3000'], // 提交（含权限校验/任务落库）
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const r = http.post(BASE + '/api/auth/login', JSON.stringify({ username: USER, password: PASS }),
    { headers: { 'Content-Type': 'application/json' } });
  const token = r.json('data.token');
  if (!token) throw new Error('setup 登录失败：' + r.body);
  return { token };
}

export default function (data) {
  const r = http.post(BASE + '/api/report/generate', JSON.stringify({ studentId: STUDENT_ID, termId: TERM_ID }),
    { headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + data.token } });
  check(r, {
    'report/generate 提交成功': (v) => v.status === 200 && v.json('code') === 0,
  });
  sleep(4 + Math.random() * 3); // 每次间隔 4~7s：2 VU ≈ 每分钟 ~20 任务，贴近期末批量高峰
}
