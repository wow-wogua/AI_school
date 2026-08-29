-- AI_school M3/M4 数据库结构（t_* 表）
-- 数字列统一 DECIMAL(10,2)：既存整数也存 107.2 / 270.39 / 0.23 等样例真值
SET NAMES utf8mb4;

-- ───────── 基础信息 ─────────
CREATE TABLE IF NOT EXISTS t_user (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(64)  NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  real_name     VARCHAR(64)  NOT NULL,
  role          VARCHAR(20)  NOT NULL COMMENT 'ADMIN/HEAD_TEACHER/TEACHER',
  phone         VARCHAR(20)  DEFAULT NULL,
  status        TINYINT      NOT NULL DEFAULT 1,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户';

CREATE TABLE IF NOT EXISTS t_grade (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(32) NOT NULL,
  school_year VARCHAR(16) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='年级';

CREATE TABLE IF NOT EXISTS t_class (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  grade_id        BIGINT NOT NULL,
  name            VARCHAR(32) NOT NULL,
  head_teacher_id BIGINT DEFAULT NULL COMMENT '→t_user',
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_grade (grade_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级';

CREATE TABLE IF NOT EXISTS t_student (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT DEFAULT NULL,
  student_no      VARCHAR(32) NOT NULL UNIQUE,
  name            VARCHAR(64) NOT NULL,
  gender          VARCHAR(4)  DEFAULT NULL,
  class_id        BIGINT NOT NULL,
  enroll_date     DATE DEFAULT NULL,
  status          VARCHAR(16) NOT NULL DEFAULT '在读',
  photo_url       VARCHAR(255) DEFAULT '',
  guardian_name   VARCHAR(64) DEFAULT NULL,
  guardian_phone  VARCHAR(20) DEFAULT NULL,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生';

CREATE TABLE IF NOT EXISTS t_term (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(32) NOT NULL UNIQUE COMMENT '2026年春季学期',
  start_date  DATE DEFAULT NULL,
  end_date    DATE DEFAULT NULL,
  is_current  TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学期';

CREATE TABLE IF NOT EXISTS t_subject (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  name         VARCHAR(32) NOT NULL UNIQUE COMMENT '学科页显示名（道德与法治/信息技术）',
  short_name   VARCHAR(32) DEFAULT NULL COMMENT '平时成绩表显示名（政治/信息）',
  type         VARCHAR(16) NOT NULL DEFAULT '国家课程',
  sort         INT NOT NULL COMMENT '学科页顺序（subjectPages）',
  regular_sort INT NOT NULL DEFAULT 99 COMMENT '平时成绩两表顺序（regularScores，样例序与学科页序不同）',
  motto        TEXT COMMENT '学科页成长箴言',
  proc_h_min   DECIMAL(10,2) DEFAULT NULL COMMENT '学科过程性评价横向图轴 min（样例刻度）',
  proc_h_max   DECIMAL(10,2) DEFAULT NULL,
  proc_h_step  DECIMAL(10,2) DEFAULT NULL,
  proc_w_max   DECIMAL(10,2) DEFAULT NULL COMMENT '周折线轴 max（step=max==3?1:max/4）',
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学科';

CREATE TABLE IF NOT EXISTS t_teach (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  teacher_id  BIGINT NOT NULL,
  class_id    BIGINT NOT NULL,
  subject_id  BIGINT NOT NULL,
  KEY idx_teacher (teacher_id),
  KEY idx_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任课关系';

-- ───────── 成绩 ─────────
CREATE TABLE IF NOT EXISTS t_exam (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  term_id          BIGINT NOT NULL,
  name             VARCHAR(64) NOT NULL COMMENT '期末考试',
  exam_date        DATE DEFAULT NULL,
  class_max_total  DECIMAL(10,2) DEFAULT NULL COMMENT '班级总分最高（排名任务回填）',
  grade_max_total  DECIMAL(10,2) DEFAULT NULL,
  create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_term (term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试';

CREATE TABLE IF NOT EXISTS t_exam_subject (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  exam_id     BIGINT NOT NULL,
  subject_id  BIGINT NOT NULL,
  full_score  DECIMAL(10,2) DEFAULT NULL,
  class_max   DECIMAL(10,2) DEFAULT NULL COMMENT '本班该科最高分',
  grade_max   DECIMAL(10,2) DEFAULT NULL COMMENT '年级该科最高分',
  KEY idx_exam (exam_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试科目';

CREATE TABLE IF NOT EXISTS t_score (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  exam_id     BIGINT NOT NULL,
  subject_id  BIGINT NOT NULL,
  student_id  BIGINT NOT NULL,
  score       DECIMAL(10,2) NOT NULL,
  class_rank  INT DEFAULT NULL,
  grade_rank  INT DEFAULT NULL,
  created_by  BIGINT DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_exam_student (exam_id, student_id),
  KEY idx_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成绩';

CREATE TABLE IF NOT EXISTS t_regular_score (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id  BIGINT NOT NULL,
  subject_id  BIGINT NOT NULL,
  term_id     BIGINT NOT NULL,
  score       DECIMAL(10,2) NOT NULL,
  `date`      DATE DEFAULT NULL,
  KEY idx_student_term (student_id, term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平时成绩';

-- p19 作业完成情况表（值形如 77(42次)，score/times 拆两列存；'-' = 无行）
CREATE TABLE IF NOT EXISTS t_homework_stat (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id  BIGINT NOT NULL,
  term_id     BIGINT NOT NULL,
  subject_id  BIGINT NOT NULL,
  col_type    TINYINT NOT NULL COMMENT '0 作业登记综合/1 未完成作业/2 有待努力/3 良好继续加油/4 优秀继续保持',
  score       DECIMAL(10,2) NOT NULL,
  times       INT NOT NULL,
  KEY idx_student_term (student_id, term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作业完成情况';

-- ───────── 九格评价引擎 ─────────
CREATE TABLE IF NOT EXISTS t_grid (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  code           VARCHAR(8) NOT NULL UNIQUE COMMENT 'GG/XG/XG2/PG/ZG/HG/MG/TG/TG2',
  name           VARCHAR(8) NOT NULL COMMENT '国格…',
  icon           VARCHAR(64) DEFAULT NULL,
  sort           INT NOT NULL,
  cur_axis_max   DECIMAL(10,2) DEFAULT NULL COMMENT '本学期横向图轴 max/step（样例刻度，随模板下发）',
  cur_axis_step  DECIMAL(10,2) DEFAULT NULL,
  prev_axis_max  DECIMAL(10,2) DEFAULT NULL,
  prev_axis_step DECIMAL(10,2) DEFAULT NULL,
  week_min       DECIMAL(10,2) DEFAULT NULL COMMENT '周折线轴',
  week_max       DECIMAL(10,2) DEFAULT NULL,
  week_step      DECIMAL(10,2) DEFAULT NULL,
  create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='九格';

CREATE TABLE IF NOT EXISTS t_indicator (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  grid_id       BIGINT NOT NULL,
  name          VARCHAR(32) NOT NULL COMMENT '课堂表现/作业表现…',
  direction     VARCHAR(4) NOT NULL COMMENT '正/负',
  default_score DECIMAL(10,2) DEFAULT 1,
  subject_scope VARCHAR(64) DEFAULT NULL,
  KEY idx_grid (grid_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='二级指标';

-- 记录卡 = 按 (title, indicator) 分组聚合：sum(score)→'+16'，登记人 = 去重教师名按首次出现顺序
CREATE TABLE IF NOT EXISTS t_evaluation (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id   BIGINT NOT NULL,
  teacher_id   BIGINT DEFAULT NULL,
  indicator_id BIGINT NOT NULL,
  title        VARCHAR(64) NOT NULL COMMENT '记录卡标题（尊师守纪/作业优秀(地生)…）',
  score        DECIMAL(10,2) NOT NULL,
  remark       VARCHAR(255) DEFAULT NULL,
  eval_time    DATETIME NOT NULL,
  KEY idx_student_time (student_id, eval_time),
  KEY idx_indicator (indicator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价记录';

-- ───────── 聚合表（定时任务/种子生成；报告只读这里） ─────────
CREATE TABLE IF NOT EXISTS t_grid_stat_week (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  term_id    BIGINT NOT NULL,
  grid_id    BIGINT NOT NULL,
  week_no    INT NOT NULL,
  score      DECIMAL(10,2) NOT NULL COMMENT '本周净得分',
  KEY idx_student_term (student_id, term_id, grid_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='九格周聚合';

CREATE TABLE IF NOT EXISTS t_grid_stat_term (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  term_id    BIGINT NOT NULL,
  grid_id    BIGINT NOT NULL,
  points     DECIMAL(10,2) NOT NULL COMMENT '获得评价积分（记录分值和）',
  eval_count INT NOT NULL COMMENT '获得评价次数',
  kind_count INT NOT NULL COMMENT '获得评价种类',
  score      DECIMAL(10,2) NOT NULL COMMENT '本格当前分（横向图/雷达用；样例心格=2.7 与 points 不同源）',
  KEY idx_student_term (student_id, term_id, grid_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='九格学期聚合';

CREATE TABLE IF NOT EXISTS t_class_grid_avg (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  class_id   BIGINT NOT NULL,
  term_id    BIGINT NOT NULL,
  grid_id    BIGINT NOT NULL,
  avg_score  DECIMAL(10,2) NOT NULL,
  KEY idx_class_term (class_id, term_id, grid_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级九格均值';

CREATE TABLE IF NOT EXISTS t_grade_grid_avg (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  grade_id   BIGINT NOT NULL,
  term_id    BIGINT NOT NULL,
  grid_id    BIGINT NOT NULL,
  avg_score  DECIMAL(10,2) NOT NULL,
  KEY idx_grade_term (grade_id, term_id, grid_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='年级九格均值';

-- 学科页周数据（智格类过程性评价按学科细分，架构聚合表族的学科维度）
CREATE TABLE IF NOT EXISTS t_subject_stat_week (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  term_id    BIGINT NOT NULL,
  subject_id BIGINT NOT NULL,
  week_no    INT NOT NULL,
  mine       DECIMAL(10,2) NOT NULL,
  class_avg  DECIMAL(10,2) NOT NULL,
  grade_avg  DECIMAL(10,2) NOT NULL,
  KEY idx_student_term (student_id, term_id, subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学科周聚合';

CREATE TABLE IF NOT EXISTS t_subject_stat_term (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id     BIGINT NOT NULL,
  term_id        BIGINT NOT NULL,
  subject_id     BIGINT NOT NULL,
  pos_mine       DECIMAL(10,2) NOT NULL,
  pos_class_avg  DECIMAL(10,2) NOT NULL,
  pos_grade_avg  DECIMAL(10,2) NOT NULL,
  neg_mine       DECIMAL(10,2) NOT NULL,
  neg_class_avg  DECIMAL(10,2) NOT NULL,
  neg_grade_avg  DECIMAL(10,2) NOT NULL,
  KEY idx_student_term (student_id, term_id, subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学科学期过程性评价聚合';

-- 学业综合页「过程性评价总分」21 周累计线（mine/class_avg/grade_avg 均为累计值）
CREATE TABLE IF NOT EXISTS t_process_week (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  term_id    BIGINT NOT NULL,
  week_no    INT NOT NULL,
  mine       DECIMAL(10,2) NOT NULL,
  class_avg  DECIMAL(10,2) NOT NULL,
  grade_avg  DECIMAL(10,2) NOT NULL,
  KEY idx_student_term (student_id, term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='过程性评价周累计';

-- 学业综合页「过程性评价总分」正/负向学期汇总
CREATE TABLE IF NOT EXISTS t_process_stat (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id     BIGINT NOT NULL,
  term_id        BIGINT NOT NULL,
  pos_mine       DECIMAL(10,2) NOT NULL,
  pos_class_avg  DECIMAL(10,2) NOT NULL,
  pos_grade_avg  DECIMAL(10,2) NOT NULL,
  neg_mine       DECIMAL(10,2) NOT NULL,
  neg_class_avg  DECIMAL(10,2) NOT NULL,
  neg_grade_avg  DECIMAL(10,2) NOT NULL,
  KEY idx_student_term (student_id, term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='过程性评价学期汇总';

-- 学业分析结论（规则引擎产出；样例「优势学科=生物」体现稳定性口径，非单纯最高分）
CREATE TABLE IF NOT EXISTS t_student_analysis (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id        BIGINT NOT NULL,
  term_id           BIGINT NOT NULL,
  advantage         VARCHAR(32) NOT NULL COMMENT '优势学科',
  to_improve        VARCHAR(32) NOT NULL COMMENT '待提升学科',
  radar_advantages  JSON DEFAULT NULL COMMENT '["智格","信格"]',
  radar_to_improve  JSON DEFAULT NULL COMMENT '["心格"]',
  KEY idx_student_term (student_id, term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学业分析结论';

-- ───────── 活动与荣誉 ─────────
CREATE TABLE IF NOT EXISTS t_activity (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  title       VARCHAR(128) NOT NULL,
  type        VARCHAR(32) DEFAULT NULL,
  start_time  DATETIME DEFAULT NULL,
  place       VARCHAR(128) DEFAULT NULL,
  cover_url   VARCHAR(255) DEFAULT NULL,
  intro       TEXT,
  creator_id  BIGINT DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动';

CREATE TABLE IF NOT EXISTS t_activity_signup (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  activity_id  BIGINT NOT NULL,
  student_id   BIGINT NOT NULL,
  signup_time  DATETIME DEFAULT NULL,
  checkin_time DATETIME DEFAULT NULL,
  award        VARCHAR(128) DEFAULT NULL,
  performance  VARCHAR(255) DEFAULT NULL,
  eval_text    VARCHAR(255) DEFAULT NULL,
  KEY idx_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动参与';

CREATE TABLE IF NOT EXISTS t_honor (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id     BIGINT NOT NULL,
  name           VARCHAR(128) NOT NULL COMMENT '奖项名称',
  level          VARCHAR(32) DEFAULT NULL COMMENT '级别（国家级/省级/市级/区级/校级/班级）',
  issuer         VARCHAR(128) DEFAULT NULL COMMENT '主办单位',
  honor_date     DATE DEFAULT NULL,
  file_url       VARCHAR(255) DEFAULT NULL COMMENT 'MinIO 对象路径 honor/{studentId}/{uuid}.{ext}',
  ai_parsed      JSON DEFAULT NULL COMMENT 'AI 视觉识别结果 {name,level,issuer,date}',
  confirm_status VARCHAR(8) NOT NULL DEFAULT '待确认' COMMENT '待确认/已确认',
  create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_student (student_id),
  KEY idx_status (confirm_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='荣誉与证书';

-- ───────── 成长银行 / 象征物 ─────────
CREATE TABLE IF NOT EXISTS t_coin_account (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id   BIGINT NOT NULL UNIQUE,
  current_coin DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '现有能量币',
  total_coin   DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '能量币总量',
  update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='能量币账户';

CREATE TABLE IF NOT EXISTS t_coin_rate (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  rate           DECIMAL(10,2) NOT NULL COMMENT '积分→能量币汇率',
  effective_date DATE NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='汇率配置';

-- module = 收入模块名（智格-学科水平…），coin = 折算能量币
CREATE TABLE IF NOT EXISTS t_coin_income (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id  BIGINT NOT NULL,
  term_id     BIGINT NOT NULL,
  source_type VARCHAR(16) NOT NULL COMMENT '评价/活动/荣誉',
  source_id   BIGINT DEFAULT NULL,
  module      VARCHAR(64) NOT NULL,
  score       DECIMAL(10,2) DEFAULT NULL,
  coin        DECIMAL(10,2) DEFAULT NULL COMMENT 'NULL = 榜位缺位（样例收入榜第4名）',
  display_order INT NOT NULL DEFAULT 0 COMMENT '模块展示序（收入 TOP 榜序；样例第4名缺位由序号体现）',
  create_time DATETIME NOT NULL,
  KEY idx_student_term (student_id, term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='能量币收入';

CREATE TABLE IF NOT EXISTS t_coin_expense (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id  BIGINT NOT NULL,
  term_id     BIGINT NOT NULL,
  item        VARCHAR(64) NOT NULL,
  coin        DECIMAL(10,2) NOT NULL,
  create_time DATETIME NOT NULL,
  KEY idx_student_term (student_id, term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='能量币支出';

CREATE TABLE IF NOT EXISTS t_coin_week (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  term_id    BIGINT NOT NULL,
  week_no    INT NOT NULL,
  in_mine    DECIMAL(10,2) NOT NULL,
  in_class   DECIMAL(10,2) NOT NULL,
  in_grade   DECIMAL(10,2) NOT NULL,
  out_mine   DECIMAL(10,2) NOT NULL,
  out_class  DECIMAL(10,2) NOT NULL,
  out_grade  DECIMAL(10,2) NOT NULL,
  KEY idx_student_term (student_id, term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='能量币周收支';

CREATE TABLE IF NOT EXISTS t_coin_stat (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id        BIGINT NOT NULL,
  term_id           BIGINT NOT NULL,
  compare_class_avg JSON NOT NULL COMMENT '[650,430,55,120]',
  compare_grade_avg JSON NOT NULL,
  KEY idx_student_term (student_id, term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='能量币对比统计';

CREATE TABLE IF NOT EXISTS t_growth_level (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  level       INT NOT NULL UNIQUE,
  min_score   DECIMAL(10,2) NOT NULL,
  symbol_name VARCHAR(64) DEFAULT NULL,
  symbol_img  VARCHAR(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成长象征物等级';

CREATE TABLE IF NOT EXISTS t_growth_symbol_stat (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  term_id    BIGINT NOT NULL,
  score      DECIMAL(10,2) NOT NULL COMMENT '成长积分（样例 1020.0）',
  KEY idx_student_term (student_id, term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成长象征物统计';

-- ───────── 综合素质 / 寄语 ─────────
CREATE TABLE IF NOT EXISTS t_comprehensive (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id  BIGINT NOT NULL,
  term_id     BIGINT NOT NULL,
  moral       CHAR(1) NOT NULL COMMENT '思想品德',
  ability     CHAR(1) NOT NULL COMMENT '学业水平',
  health      CHAR(1) NOT NULL COMMENT '身心健康',
  aesthetic   CHAR(1) NOT NULL COMMENT '艺术素养',
  practice    CHAR(1) NOT NULL COMMENT '社会实践',
  final_level CHAR(1) NOT NULL,
  KEY idx_student_term (student_id, term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='综合素质评定';

CREATE TABLE IF NOT EXISTS t_comment (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id  BIGINT NOT NULL,
  term_id     BIGINT NOT NULL,
  type        VARCHAR(8) NOT NULL COMMENT '班主任/学生自评/家长',
  content     TEXT COMMENT '正式内容（确认后报告用这里）',
  ai_draft    TEXT COMMENT 'AI 草稿',
  status      VARCHAR(8) NOT NULL DEFAULT '草稿' COMMENT 'AI草稿/已修改/已确认',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_student_term (student_id, term_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='寄语';

-- ───────── 报告 ─────────
-- sections JSON：school 版块文案（intro/nineGridIntro/philosophy）+ 雷达 motto/max + 能量币各图轴刻度（样例固定刻度随模板下发）
CREATE TABLE IF NOT EXISTS t_report_template (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  school_name VARCHAR(64) NOT NULL,
  sections    JSON NOT NULL,
  status      VARCHAR(8) NOT NULL DEFAULT '启用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告模板';

CREATE TABLE IF NOT EXISTS t_report_task (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  term_id     BIGINT NOT NULL,
  scope       VARCHAR(8) NOT NULL COMMENT '单生/班级/年级',
  target_id   BIGINT NOT NULL COMMENT 'student_id 或 class_id 或 grade_id',
  status      VARCHAR(8) NOT NULL DEFAULT '排队' COMMENT '排队/进行中/成功/失败/部分失败',
  total       INT NOT NULL DEFAULT 0,
  done        INT NOT NULL DEFAULT 0,
  failed      INT NOT NULL DEFAULT 0,
  create_by   BIGINT DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告生成任务';

CREATE TABLE IF NOT EXISTS t_report (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id    BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  term_id    BIGINT NOT NULL,
  file_url   VARCHAR(255) DEFAULT NULL COMMENT 'MinIO 对象路径',
  page_count INT DEFAULT NULL,
  gen_time   DATETIME DEFAULT NULL,
  status     VARCHAR(8) NOT NULL DEFAULT '排队' COMMENT '排队/渲染中/成功/失败（失败重试复用本行）',
  error      VARCHAR(500) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_task (task_id),
  KEY idx_student (student_id, term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成品报告';

CREATE TABLE IF NOT EXISTS t_ai_task (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_type    VARCHAR(16) NOT NULL COMMENT 'COMMENT=寄语草稿 / SUMMARY=成长总结',
  student_id   BIGINT NOT NULL,
  term_id      BIGINT NOT NULL,
  status       VARCHAR(8) NOT NULL DEFAULT '排队' COMMENT '排队/生成中/成功/失败',
  source       VARCHAR(16) DEFAULT NULL COMMENT 'llm/template（成功时）',
  result_json  LONGTEXT DEFAULT NULL COMMENT '结果 JSON（同同步接口返回体 data）',
  error        VARCHAR(500) DEFAULT NULL,
  created_by   BIGINT NOT NULL,
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  started_time DATETIME DEFAULT NULL,
  finished_time DATETIME DEFAULT NULL,
  KEY idx_creator (created_by, id),
  KEY idx_student (student_id, term_id, task_type),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 分析任务队列';
