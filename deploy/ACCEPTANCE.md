# AI_school 项目二 M3/M4 验收记录

日期：2026-08-19　环境：Windows 11 + Docker（aischool-mysql / aischool-redis / aischool-minio）
账号：admin/admin123（管理员）、litao|zhaolaoshi/aischool123（班主任 初一(1)班/初一(2)班）、wanglaoshi/aischool123（任课 初一(2)班）

| # | 验收标准 | 结果 | 证据 |
|---|---------|------|------|
| ① | 契约零漂移：聚合 JSON 与 golden_student.json 全等；渲染 50 页、标题序列与 target/report.pdf 逐页一致 | **PASS** | `report-server/scripts/verify_contract.py`：聚合 JSON 深比较全等（数值 int/float 按 JS 等价）；Java 渲染核心 50 页，PyMuPDF 提取两份 PDF 标题序列逐页一致。服务端接口 `GET /api/report/data/1?termId=2` 与库内数据同源 |
| ② | 单份 30s 内出 PDF，可预览可下载 | **PASS** | 空闲时单份 16s、批量满载时插队单份 24s（`scripts/verify_concurrency.py`）；PDF inline 预览 200 application/pdf，attachment 下载头 + 完整落盘（E2E 校验 `%PDF-` 头与字节数） |
| ③ | 50 人班级批量 ≤5 分钟、失败可重试、进度实时 | **PASS** | 50/50 成功 0 失败，189s（两次复跑 178s/189s）；进度经 Redis 单调递增（序列见 verify_concurrency 输出）；破坏渲染 classpath → 任务失败 failed=2 → 还原 → retry → 20s 内全成功（`scripts/verify_retry.py`，自恢复可反复跑） |
| ④ | 并发 ≥4 时 Web 无劣化、批量中单份互不干扰 | **PASS** | 渲染池并发 6 满载批量期间：任务查询 API 最差 0.01s；单份优先级插队 24s 完成；批量中已出 PDF 下载完整 635105B |
| ⑤ | 手机/平板/电脑三档全流程（发起→看进度→预览→下载） | **PASS** | `report-web/e2e/verify_web.mjs` 32/32：三档（390×844 / 768×1024 / 1440×900）各完成 登录→批量发起→实时进度到 100%→PDF 预览（200）→下载（落盘校验）→寄语 AI 草稿，全部页面无横向滚动；截图 `report-web/e2e/shots/` |
| ⑥ | 未登录 401、越权 403、角色隔离 | **PASS** | `scripts/verify_rbac.py` 18/18：无/坏 token→401×4；班级/学生列表按角色过滤；班主任跨班读写→403×3；任课教师发起生成/批量/寄语→403×3；本班操作全通过；分页 total 正确 |

## AI 服务层（功能点）
`scripts/verify_ai.py` 11/11：学业分析（规则引擎硬数字：优势/待提升/趋势/预警）→ 寄语 AI 草稿（未配 LLM 走确定性模板，配置 OpenAI 兼容 API 即切换，无需改码）→ 草稿存 `aiDraft` 不覆盖已确认内容 → 人工编辑确认 → 聚合 JSON 的 headTeacherComment 即确认后内容（AI 只产草稿闭环）。成长总结四块（亮点/学习/素质/建议）。

## 复跑（全部永久脚本）
```
cd report-server
python scripts/verify_contract.py      # ①
python scripts/verify_concurrency.py   # ②③④（约 4 分钟）
python scripts/verify_retry.py         # ③ 失败重试
python scripts/verify_rbac.py          # ⑥
python scripts/verify_ai.py            # AI 层
cd ../report-web && node e2e/verify_web.mjs   # ⑤（需后端 8080 + vite 5173）
```

## 过程中修复的关键问题
1. **PDF 下载随机截断**：StreamingResponseBody 异步派发再次穿过 Security 链，JWT 过滤器（OncePerRequestFilter）跳过 ASYNC 派发 → 上下文空 → 连接被掐。改为 byte[] 同步返回（PDF 约 600KB）。
2. **Redis 空转异常**：Lettuce command timeout(5s) 与 BLPOP 阻塞(5s) 同值赛跑 → timeout 提到 20s。
3. **分页 total=0**：未注册 MyBatis-Plus PaginationInnerInterceptor → `config/MybatisPlusConfig.java`。
4. **前端草稿不回填**：接口返回 `{draft}`，前端误读 `{content}`。

---

# M6 追加验收（活动 / 荣誉 / 时间轴）— 2026-08-19

范围：§9 活动管理（CRUD+录参与+能量币入账）、§10 荣誉与证书（上传→AI 视觉识别→教师确认，未配 AI 自动降级手动）、§8 成长时间轴（评价/活动/荣誉/成绩进步统一事件流）、渲染模板 p42「我的活动」页支持非空活动表（空态 DOM 一字不动，页数恒 50）。§14 学生自评、§15 家长寄语未实现（用户确认砍掉）。

| 验证 | 结果 | 证据 |
|---|------|------|
| M6 后端全量 | **PASS 31/31** | `report-server/scripts/verify_m6.py`：活动 CRUD/带参与拒删(400)；录获奖→termId 按日期推导=2、coin.total +20、聚合 activities 进报告、收入 TOP5 榜序不受扰；荣誉 上传(AI 未配置→source=manual)→确认→+30、重复确认 400、待确认可删、原件 200；时间轴三类事件倒序、学生1 无活动/荣誉事件；单份渲染 50 页且活动页含表格（空态消失）；RBAC（班主任建活动 403/跨班录参与 403/本班 200/任课 403/未登录 401）；学生 1 首尾零污染 |
| M6 前端 | **PASS 20/20** | `report-web/e2e/verify_m6_web.mjs`：桌面 1440×900 + 手机 390×844，zhaolaoshi 录参与 / 上传证书→填表→确认（含能量币）/ 时间轴断言（≥3 事件、含「荣获」、含荣誉），三页均无横向滚动；班主任看不到「新建活动」按钮 |
| 全量回归 | **PASS** | verify_contract ①零漂移仍 PASS；verify_rbac 18/18；verify_ai 11/11；verify_retry 4/4；verify_concurrency 7/7（50 人 170s）；verify_web 32/32 |

复跑 M6：
```
cd report-server && python scripts/verify_m6.py            # --skip-render 可跳过渲染断言
cd ../report-web && node e2e/verify_m6_web.mjs
```

实现要点：t_honor 新表（ai_parsed JSON + confirm_status）；CoinLedgerService 唯一入账点（termId 按活动/荣誉日期落 t_term 区间推导，display_order=99 不进收入 TOP5）；活动/荣誉 module 命名「活动-<标题>」「荣誉-<名称>」；无日期荣誉不进时间轴（按学期过滤）；verify_retry 移动渲染 lib 偶发 Windows 文件锁——删 lib.bak 残留重跑即可。

---

# M7 追加验收（功能点全量补齐：管理端 / 成绩排名 / 评价引擎 / 综评 / 封面 / 年级批量）— 2026-08-20

范围：§1 用户与权限（账号/任课/年级班级学生/学期/指标/报告模板管理端全量 CRUD+删除守卫）、§2 学生照片（MinIO 上传/覆盖/取图/删除清理，档案缩略图，不进 PDF）、§3 成绩管理（考试/录入/竞争排名 1,2,2,4/Excel 模板下载+导入）、§5/§6 日常评价引擎（一次评价写穿 6 个目标：明细→学期格→周格→能量币→班/年级均值）、§7 综合素质五维（final=众数并列取高）、§9 活动封面（MinIO 存取）、§13 成长总结前端页、年级批量报告（52 份）。§14 学生自评、§15 家长寄语维持不做。设计细节见 架构设计.md §十一（§2 照片见 §11.8）。

| 验证 | 结果 | 证据 |
|---|------|---|
| M7 后端 | **PASS 89/89** | `report-server/scripts/verify_m7.py`（自带 mysqldump 24 表快照信封，跑毕恢复 + 全量契约复验）：管理端 29 断言（账号生命周期/停用拒登录/删除守卫 400×4/学期当前单活切换后还原/非 ADMIN 403/学生照片 6——上传前缀、覆盖换对象、GET 字节一致、非图片 400、教师 403、未上传 404）；指标引用守卫 6（被引用改名/删除 400）；模板锁 6（启用 PUT/DELETE/切换 400，草稿 CRUD 200）；成绩 12（考试 2026-06-13 保 latestExam 稳定、排名含同分并列、任课跨学科 403、班主任跨班 403、class1 只写学生 2、maxes 回填、xlsx PK 魔数、导入含 skip、重录幂等）；评价引擎 17（智格 +2 写穿 diff：points/count/radar/第 18 周能量币/记录卡/classAvg +1、gradeAvg +2/52；kinds==记录卡数自洽；学生 1 除 gradeAvg 外零差异）；综评 7（三组 final 规则+权限 403）；封面 4；总结 API 2；年级批量 5（52 份 185s 零失败，班主任/任课 403） |
| M7 前端 | **PASS 42/42** | `report-web/e2e/verify_m7_web.mjs`（自带快照信封，评价写完恢复）：桌面 1440×900 + 手机 390×844 × 三角色——admin（UI 建考试/系统管理六页签/模板锁定标记/报告列表年级视角批量按钮）；wanglaoshi（语文可编辑、数学只读态、UI 录分后出名次、评价提交+历史表）；zhaolaoshi（综评五维保存 final=A、成长总结四块卡片）；全部页面无横向滚动 |
| 全量回归 | **PASS 10/10** | contract（聚合 JSON 零漂移+50 页逐标题一致）/ rbac 18 / ai 11 / retry 4 / concurrency 7（空闲单份 18s）/ web 32 / m6 31 / m6_web 20 / m7_web 42 / m7 83→**89**（§11.8 学生照片补齐后单独复跑 PASS，其余 9 脚本不受该改动影响——改动仅 admin 照片端点 + StudentTab 页；信封内 verify_contract 已随 m7 复验）—— `report-server/scripts/run_regression.sh` 一键复跑；retry 首跑遇 M6 已记录的 Windows 渲染 lib 文件锁，清 lib.bak 残留重跑即过 |

**契约两处有据偏差**（详见 架构设计.md §11.3）：① ReportDataBuilder 追加 1 行 `ORDER BY id` 决胜序——3796 条同刻评价的顺序确定化，DB id 序与 golden 序一致，免重灌种子；② gradeAvg 年级共享——初一(1)(2)班同属一个年级，对同学级学生写评价必然平移学生 1 的 gradeAvg s/52，测试以「除 gradeAvg 外零差异」断言 + 快照信封恢复兜底。种子 kind_count 的生成器抖动作「种子归一」说明：写路径按记录卡同构口径重算，首次写入后 `kinds == 记录卡数` 恒成立（自洽且幂等）。

复跑 M7 / 全链：
```
cd report-server && python scripts/verify_m7.py        # ~5.5 分钟（含年级批量）
cd ../report-web && node e2e/verify_m7_web.mjs          # 需后端 8080 + vite 5173
bash scripts/run_regression.sh                          # 十脚本全链（从 report-server 目录）
```

---

# 前端风格统一验收（任务 #17：数智成长主题）— 2026-08-20

范围：整站视觉统一为「数智成长 · 初中素质报告平台」主题（靛蓝 #4F46E5 → 青绿 #10B981，初中场景专业+活泼+青春）。纯 CSS + Vue transition 零新依赖：style.css 全局设计令牌层（EP CSS 变量梯子全套覆写，13 视图 + 6 管理页签自动换装）+ App.vue 渐变页头/SVG 品牌/路由过渡 + 登录页渐变 hero + EP 图标全局注册 + 5 视图 page-title 轻触。E2E 94 断言依赖的文本/选择器顺序/EP 钩子/无横滚全部保留；修复路由过渡离场竞态（离场零时长，详见 架构设计.md §11.9）。

| 验证 | 结果 | 证据 |
|---|------|---|
| 构建门禁 | PASS | `npm run build`（vue-tsc + vite）零错，8.3s |
| Web 全量 | **PASS 94/94** | verify_web 32/32 + verify_m6_web 20/20 + verify_m7_web 42/42（m7 首跑 1 条时序抖动，复跑两次 42/42）；390/768/1440 三档视口全部页面无横向滚动 |

复跑：`cd report-web && node e2e/verify_web.mjs && node e2e/verify_m6_web.mjs && node e2e/verify_m7_web.mjs`（需后端 8080 + vite 5173）

---

# PDF 模板图片嵌入验收（打印第 6 页重叠修复 + 前八页原版图片还原）— 2026-08-20

范围：从原版模板 学生成长报告册.pdf 提取前八页图片（smask 合并 alpha、降至 ~300dpi、12 张 1.9MB）内联进渲染器：p04 九格插画替换 9 个 CSS 圆（修复打印第 6 页与页脚重叠）、p03 照片×2 + 标题图标、p05 校长寄语原版整页图、p02 理念底图、p06 角饰、p07/p08-18 角饰装饰、页眉 logo 全内容页注入。详见 架构设计.md §11.10。

| 验证 | 结果 | 证据 |
|---|------|---|
| 渲染合同 | PASS | verify_contract.py：聚合 JSON 与 golden 全等；50 页、每页标题序列与基线逐页一致 |
| 重叠修复 | PASS | 生成 PDF 打印第 6 页（p04）790–840pt 页脚带非白仅 1.5%（仅页码线；插画底缘 672pt） |
| 图片落位 | PASS | 逐区域像素检查（p02 底图/p03 照片×2/p04 插画/p05 整页/p06 角饰×2/p07/p08 角饰/logo 抽查 13 页）全部命中 |
| E2E 回归 | **PASS 94/94** | verify_web 32/32（含服务端批量渲染下载 PDF 2.76MB）+ m6 20/20 + m7 42/42 |
| 目检材料 | — | docs/img_embed_check/cmp_p01..08.png（原版 vs 生成并排） |

复跑：`cd report-renderer && mvn -s settings.xml package -DskipTests`，然后 `cd ../report-server && PYTHONIOENCODING=utf-8 python scripts/verify_contract.py`（需后端 8080）

## 2026-08-22 增补验收：一键容器化 + AI 接入 + AI 任务队列

范围：①docker compose 五容器一键部署（详见 架构设计.md §11.11）；②DeepSeek 真实 key 三类 AI 功能接入与降级链路；③AI 分析任务化 t_ai_task（切页/关浏览器不中断、整班批量、并发 16、用户隔离，详见 §11.12）。

| 验证 | 结果 | 证据 |
|---|------|---|
| 五容器部署 | PASS | mysql/redis/minio/report-server/report-web 全 healthy；web 80 端口可访问 |
| 容器化 E2E 回归 | **PASS 94/94** | BASE_URL=http://localhost 下 verify_web 32 + m6_web 20 + m7_web 42 |
| 真实 key AI（flash） | PASS | 寄语/总结 source=llm；证书 vision 识别 4 字段全中；坏 key 自动降级模板零崩溃 |
| AI 任务 API | **PASS 9/9** | 去重复用 / 并发上限实测=16 且超出发排队（17 任务：生成中 16 排队 1）/ 排队位次 / 草稿落库 / mine 用户隔离 0 泄漏 |
| AI 任务 UX | **PASS 12/12** | 切页后台继续 + badge、切回自动恢复、浏览器重开恢复、本班全部生成、任务面板（状态/学生名）、总结四块渲染、console 0 错 |
| LLM 输出健壮性 | PASS | Markdown 前缀/标题独行兼容（四块全解析）；慢响应 61s 完成不降级 |

复跑：AI 任务 API 验证思路——POST /api/ai/tasks 后轮询 /api/ai/tasks/{id}（状态/queuePosition/result）与 /api/ai/tasks/mine；E2E 基线须关闭 AI（override 移开重启 server），跑完恢复。
