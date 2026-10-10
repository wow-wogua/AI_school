# 稳定性压测框架（批44③，k6）

## 为什么是 k6

单二进制（winget `GrafanaLabs.K6`）+ 纯 JS 场景 + 内置 thresholds 断言（p95 延迟/错误率/检查通过率），
结果 JSON 导出可归档对比。比 JMeter 轻（无需 JVM/图形界面），比 ab/wrk 表达力强（登录态/多端点/思考时间）。

## 场景与档位

| 档位 | 命令 | 规格 | 验证什么 |
|---|---|---|---|
| smoke | `bash run.sh smoke` | 2 VU / 30s | 改动后快速回归（低于阈值直接红） |
| load | `bash run.sh load` | 20 VU / 3m | 日常峰值：全校 487 师+家长混合并发，20 活跃用户已偏高 |
| soak | `bash run.sh soak` | 10 VU / 15m | 稳定性：内存泄漏/连接池耗尽/慢查询累积（对照 stats.log 资源曲线） |
| report | `bash run.sh report` | 2 VU / 2m | 报告生成重任务：渲染队列吞吐、任务不丢、worker 稳定 |

- **core-api.js**（smoke/load/soak）：登录（~10% 迭代真登录）+ 通知列表/未读数 + 请假列表 + 报告列表 + 学生列表，思考时间 1~3s 模拟真人。
- **report-gen.js**（report）：单生 TERM 报告提交，间隔 4~7s（≈20 任务/分钟，贴近期末批量高峰）。

## 阈值（thresholds，超标 k6 退出码非 0）

- `http_req_failed < 1%`
- 读接口 `p(95) < 800ms`；登录 `p(95) < 1500ms`；报告提交 `p(95) < 3000ms`
- `checks > 99%`（业务 code=0 校验，不只 HTTP 200）

## 红线

1. **只压本地栈**（默认 `http://localhost:8080`）。生产是正式数据，只做 `/api/ping` 探活——压生产会污染业务表并占用学校带宽。
2. **report 档产生真实报告任务/报告行**（t_report_task/t_report + MinIO 对象）：跑完验收指标后按 `create_time` 清 DB 新增行；MinIO `report/` 前缀对象可留（下次渲染同名覆盖，不影响功能）。
3. 账号默认 `litao`（本地栈班主任演示号）；`USER/PASS/STUDENT_ID` 环境变量可覆盖。

## 结果归档与解读

结果落 `local/perf-results/<时间戳>_<档位>/`：

- `summary.json`：k6 汇总指标（可跨版本 diff）
- `console.log`：终端完整输出（含 per-endpoint 分位）
- `stats.log`：每 5s 一行 `时刻 server-cpu/mem | mysql-cpu/mem`——soak 档看曲线是否单调爬升（内存泄漏信号）

判读口径：load 档 p95 读 < 800ms 且错误率 < 1% = 通过；soak 档通过标准 = load 同款 + stats.log 内存曲线平稳（15 分钟内涨幅 < 20%）。

## 已知基线（2026-10-10，本地栈，开发机）

见 `local/perf-results/` 首轮记录。生产服务器规格更低（4C8G 需求单），上线后如需复测，
在服务器本地起 k6 压 `http://localhost:8080`（绕过 nginx 网络层，单测应用层），nginx 层用 smoke 档短时验证即可。
