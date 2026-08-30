# 数智成长 · 中学素质报告平台

围绕「**采集数据 → 形成成长档案 → AI 分析 → 教师干预 → 生成成长报告**」建设的中学素质报告平台。
教师日常录入成绩与过程性评价，系统自动聚合九维综合素质、成长时间轴、活动、荣誉与综评，AI 生成学业分析与班主任寄语，最终一键渲染 **51 页 1:1 复刻纸质模板**的《学生成长报告册》PDF（支持单生 / 班级 / 全年级批量）。

## 功能亮点

- **报告生成**：Playwright 无头渲染 1:1 复刻纸质模板（`学生成长报告册.pdf` 为复刻基准），中文完美嵌入；单份 30 秒内出稿（实测空闲约 16 秒）、50 人班级批量约 3 分钟，批量并发可配；聚合数据与设计基线「零漂移」契约验证
- **AI 草稿**：OpenAI 兼容协议接入大模型（DeepSeek/通义/智谱等均可），生成班主任寄语与成长总结草稿；多模态模型可自动识别荣誉证书图片字段；**未配置 key 时自动降级**为规则模板，硬数字始终由规则引擎计算
- **AI 后台任务队列**：寄语/总结提交后切页面、关浏览器照跑不中断，回来自动恢复；支持整班批量生成，顶部「生成中心」统一查看进行中/已完成任务；多教师共用队列，并发贴合模型供应商限额
- **微光信箱**：教师手机随手拍学生闪光瞬间（照片 + 场景标签 + 圈选学生）→ 班级动态轮播 / 学生「闪光时刻」→ 注入 AI 寄语与总结上下文 → 汇入报告 PDF「成长掠影」版块（无微光学生页数不变，契约不动）
- **教师档案**：工号 / 职称 / 教龄 / 任教学科 / 照片等本人维护（照片存 MinIO，工号全校唯一双保险），管理端教师管理可查看全员档案
- **双端形态**：桌面 Element Plus（报告工作台 + 系统管理）+ 移动 Vant 4 教师工作台（首页 / 班级 / 微光随手拍 / 成长记录流 / 通知 / 我的），同一工程同一鉴权，桌面/平板/手机三档适配
- **九维综合素质**：成绩/德育/活动/荣誉多维聚合，能量币成长激励体系
- **运维与扩容**：新生 Excel 导入、整班调班（升年级/分班）、整班毕业/转出一键流转（历史数据与报告保留可查）；成绩/寄语导出 xlsx；AI 用量统计（按日/按教师 tokens）；Flyway 迁移、写操作审计、登录防爆破、日志轮转、备份脚本；Swagger UI 交互式接口文档
- **RBAC 权限**：管理员 / 班主任 / 任课教师三级数据域隔离（18 项权限用例全过）
- **一键容器化**：根目录 `docker compose up -d --build` 拉起全栈（MySQL/Redis/MinIO/后端/前端），首启自动导表+种子数据

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 + TypeScript + Element Plus（桌面）+ Vant 4（移动）+ Pinia + motion-v |
| 后端 | Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis |
| 渲染 | Playwright (Chromium) + Thymeleaf + ECharts + Noto CJK 中文字体 |
| 对象存储 | MinIO（报告 PDF / 微光与证书照片 / 档案照片） |
| 部署 | Docker Compose 五服务 + Nginx 反代 |

## 快速开始

前置：已装 Docker（建议主机 ≥ 8GB 内存；后端镜像含 Chromium 与中文字体约 5.5GB）。

```bash
docker compose up -d --build
```

- 平台入口：http://localhost/（前端 80，后端 API 8080 由 Nginx 反代）
- 首次构建会从阿里云拉取 Maven/npm 依赖，耗时较长属预期；再次构建走缓存
- 首次启动自动建库（schema + 种子数据），空库即得可演示数据

### 启用 AI（可选）

```bash
cp docker-compose.override.yml.example docker-compose.override.yml
# 编辑 override 填入你的 API key，然后：
docker compose up -d report-server
```

不配置则 AI 功能自动降级（寄语走规则模板、证书手动录入），其余功能不受影响。详见《docs/接口文档.md》AI 接入与模型切换指南。

### 本机开发（改代码热调，不用重建镜像）

```bash
docker compose up -d mysql redis minio   # 只起基础设施（数据在卷里，与全栈共用）
cd report-renderer && mvn package -q     # 渲染核心先打一次包（后端渲染进程用它的 target/）
cd ../report-server && mvn spring-boot:run   # 后端 8080（连接本机 3306/6379/9000，走 application.yml 默认值）
cd ../report-web && npm install && npm run dev  # 前端 5173（vite 代理 /api → 8080）
```

回归验证（需后端 + 前端 dev 均在跑）：`bash report-server/scripts/run_regression.sh`（本机指定 Python 解释器：`PY=<路径> bash ...`）。
配置无写死路径——仓库可在任意目录克隆，各模块按相对路径互相定位。

## 演示账号

| 账号 | 密码 | 角色 |
|---|---|---|
| admin | admin123 | 学校管理员（全量） |
| litao | aischool123 | 班主任·初一(1)班（50 人，契约基线，请勿改数据；含教师档案演示数据） |
| zhaolaoshi | aischool123 | 班主任·初一(2)班（2 人，演示随意用） |
| wanglaoshi | aischool123 | 任课教师·初一(2)班 |

默认演示学期：termId=2（2026 春季）。MySQL root/aischool123，MinIO aischool/aischool123。

## 目录结构

```
├─ Dockerfile / docker-compose.yml / nginx.conf   # 一键容器化（apt/npm 国内镜像源）
├─ report-web/      # Vue3 前端（桌面 EP + 移动 Vant 双形态，含 e2e 冒烟与 qa 脚本）
├─ report-server/   # Spring Boot 后端（Flyway 迁移 + 审计 + 防爆破）
├─ report-renderer/ # Playwright 渲染核心（51 页报告，golden 生成器=换校单一事实源）
├─ deploy/          # schema.sql / seed.sql / backup.sh
├─ docs/            # 接口文档
├─ .github/         # CI（推送验证后端打包 + 前端构建）
├─ 使用手册.md      # 面向学校老师/管理员的操作手册
└─ 功能点.md        # 需求原文（第二部分为本项目）
```

## 文档索引

- **使用手册.md** —— 面向学校老师/管理员的操作手册（账号、成绩、报告、日常记录、常见问题）
- **docs/接口文档.md** —— REST API 全量说明 + AI 接入与模型切换指南
- **功能点.md** —— 需求原文（第一部分为另一项目，第二部分为本项目 16 个功能点）

## 质量验证

- 服务端回归 10 脚本约 160 断言（契约零漂移 / RBAC 18 / AI 11 / 重试 / 并发 / M6 / M7 89）+ 容器自检脚本
- 前端 E2E 冒烟 94 断言全绿（桌面 1440 + 手机 390 双档：批量生成/预览下载/荣誉证书/时间轴/综合素质等）；移动端工作台另有 qa_*.mjs 质检脚本组
- 空库首启自动种子；批量 51 页 PDF 中文渲染无方块（含微光掠影学生 52 页）
- 容器化实测：五容器 healthy、nginx:80 端到端、容器内渲染 PDF 中文无方块（2026-08-22）
- 推送到 main 自动跑 CI（后端 mvn package + 前端 npm build）

## 开发与维护须知

1. **E2E 回归必须关闭 AI**（把 override 改名移开后 `docker compose up -d report-server`）：真实大模型延迟 9~61 秒波动，会击穿 E2E 等待并消耗额度；跑完恢复 override 即可。
2. **全新空库跑 m6 E2E 前需先造数据**：seed 只建账号不建活动/荣誉，`verify_m6_web` 依赖已存在的活动与已确认荣誉。
3. `verify_m7_web` 偶发时序抖动（历史出现过一次 41/42），复跑即绿，脚本本身无问题。
4. **渲染器改动**：`cd report-renderer && mvn package -DskipTests` 即生效（每次渲染 fork 新 JVM 读最新 classpath），无需重启后端；不要 `mvn clean` 后不起服务就期望能渲染（渲染 classpath 在 target/ 下）。
5. **契约基线不入库**（`report-renderer/target/report.pdf` 是本机产物）：新机器或模板改动后刷新基线再跑契约——
   ```bash
   cd report-server && PYTHONIOENCODING=utf-8 python scripts/verify_contract.py   # 渲染学生1 出 target/contract-check/agg.pdf
   cp report-server/target/contract-check/agg.pdf ../report-renderer/target/report.pdf
   # 重跑 verify_contract.py 应双 PASS（① 聚合≡golden ② 51 页标题序列≡基线）
   ```
6. **换校 SOP**（半天级完成一校换装，历史已验证东华→石实）：改 `report-renderer/scripts/expand_golden.py` 的 school 块（intro/九维理念六条/motto，长度同量级防溢出）→ 重跑 expand_golden.py 与 `report-server/scripts/seed_db.py` → 导库（`docker exec -i aischool-mysql mysql --default-character-set=utf8mb4 -uroot -paischool123 ai_school < deploy/seed.sql`）→ 替换 4 张静态图（`img_photo1/2.jpg` 3:2、`img_logo.png` 492×424 椭圆、`img_principal.png` 1414×2000）→ 前端 `public/` 资产与文案 → 按上条刷新契约基线。
7. `tools/m2-repo` 不入库（本机构建加速缓存，gitignore 只留 `.gitkeep`）：克隆后首次 `docker compose build` 由阿里云镜像全量拉取 Maven 依赖，耗时较长属预期。
8. Docker Desktop 偶发整体崩溃（三容器同灭）时后端报 500——先 `docker ps` 看容器，等 healthy 即自愈。
9. MinIO 中报告随重新生成累积（每次生成插入新记录），磁盘配额需关注；`deploy/backup.sh` 建议挂 cron 每日备份。
10. 公网上线检查单（改 JWT 密钥/三件默认密码/关 Swagger/CORS 白名单/演示账号改密等）由维护方内部资料承接。
