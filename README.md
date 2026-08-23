# 数智成长 · 中学素质报告平台

围绕「**采集数据 → 形成成长档案 → AI 分析 → 教师干预 → 生成成长报告**」建设的中学素质报告平台。
教师日常录入成绩与过程性评价，系统自动聚合九格综合素质、成长时间轴、活动、荣誉与综评，AI 生成学业分析与班主任寄语，最终一键渲染 **50 页 1:1 复刻纸质模板**的《学生成长报告册》PDF（支持单生 / 班级 / 全年级批量）。

## 功能亮点

- **报告生成**：Playwright 无头渲染 1:1 复刻纸质模板（`学生成长报告册.pdf` 为复刻基准），中文完美嵌入，单份约 5 秒，批量并发可配
- **AI 草稿**：OpenAI 兼容协议接入大模型（DeepSeek/通义/智谱等均可），生成班主任寄语与成长总结草稿；多模态模型可自动识别荣誉证书图片字段；**未配置 key 时自动降级**为规则模板，硬数字始终由规则引擎计算
- **AI 后台任务队列**：寄语/总结提交后切页面、关浏览器照跑不中断，回来自动恢复；支持整班批量生成，顶部「生成中心」统一查看进行中/已完成任务；多教师共用队列，并发贴合模型供应商限额
- **九格综合素质**：成绩/德育/活动/荣誉多维聚合，能量币成长激励体系
- **运维与扩容**：新生 Excel 导入、整班调班（升年级/分班）、整班毕业/转出一键流转（历史数据与报告保留可查）；成绩/寄语导出 xlsx；AI 用量统计（按日/按教师 tokens）；Flyway 迁移、写操作审计、登录防爆破、日志轮转、备份脚本；Swagger UI 交互式接口文档
- **RBAC 权限**：管理员 / 班主任 / 任课教师三级数据域隔离（18 项权限用例全过）
- **响应式前端**：手机 / 平板 / 电脑三档适配，motion-v 弹性动效（尊重系统"减弱动态效果"）
- **一键容器化**：根目录 `docker compose up -d --build` 拉起全栈（MySQL/Redis/MinIO/后端/前端），首启自动导表+种子数据

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 + TypeScript + Element Plus + Pinia + motion-v |
| 后端 | Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis |
| 渲染 | Playwright (Chromium) + Noto CJK 中文字体 |
| 对象存储 | MinIO（报告 PDF / 证书图片） |
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

不配置则 AI 功能自动降级（寄语走规则模板、证书手动录入），其余功能不受影响。详见《交付文档.md》AI 接入章节。

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
| litao | aischool123 | 班主任·初一(1)班（50 人，契约基线，请勿改数据） |
| zhaolaoshi | aischool123 | 班主任·初一(2)班（2 人，演示随意用） |
| wanglaoshi | aischool123 | 任课教师·初一(2)班 |

默认演示学期：termId=2（2026 春季）。MySQL root/aischool123，MinIO aischool/aischool123。

## 目录结构

```
├─ Dockerfile / docker-compose.yml / nginx.conf   # 一键容器化（apt/npm 国内镜像源）
├─ report-web/      # Vue3 前端（含 e2e 冒烟脚本）
├─ report-server/   # Spring Boot 后端（Flyway 迁移 + 审计 + 防爆破）
├─ report-renderer/ # Playwright 渲染核心（50 页报告）
├─ deploy/          # schema.sql / seed.sql / backup.sh / 验收记录
├─ docs/            # 接口文档 + 样例报告逐页基准图
├─ .github/         # CI（推送验证后端打包 + 前端构建）
├─ 交付文档.md / 架构设计.md
```

## 文档索引

- **交付文档.md** —— 功能清单、部署步骤、账号、AI 接入、验收结论
- **架构设计.md** —— 模块划分、数据流、关键决策（append-only 决策记录）
- **接口文档.md**（docs/）—— REST API 全量说明
- **deploy/ACCEPTANCE.md** —— 里程碑验收记录

## 质量验证

- 后端 RBAC 权限用例 18/18 通过
- 前端 E2E 冒烟 94 断言全绿（桌面 1440 + 手机 390 双档：批量生成/预览下载/荣誉证书/时间轴/综合素质等）
- 空库首启自动种子；批量 50 页 PDF 中文渲染无方块
- 容器化实测：五容器 healthy、nginx:80 端到端、容器内渲染 PDF 中文无方块（2026-08-22）
- 推送到 main 自动跑 CI（后端 mvn package + 前端 npm build）
