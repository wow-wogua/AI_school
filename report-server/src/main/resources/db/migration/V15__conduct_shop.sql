-- V15（2026-09-19）：操行分规则账本 + 成长银行兑换（批3）
-- 拍板：①操行分=规则账本（基础分+事件加减+ABCD 阈值，全校一套可配）
--      ②事件联动按指标配：coin_value NULL=能量币按 score 原值（现状零变化）、
--        conduct_value NULL=不联动操行分
--      ③兑换=教师 App 录入（选学生+商品→验余额原子扣账）
ALTER TABLE t_indicator
  ADD COLUMN coin_value DECIMAL(10,2) NULL COMMENT '能量币联动值（NULL=按 score 原值入账，行为同旧版）',
  ADD COLUMN conduct_value DECIMAL(10,2) NULL COMMENT '操行分联动增减（NULL=该指标不联动操行分）';

CREATE TABLE IF NOT EXISTS t_conduct_rule (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  base_score   DECIMAL(10,2) NOT NULL DEFAULT 100 COMMENT '每学期基础分',
  grade_a_min  DECIMAL(10,2) NOT NULL DEFAULT 90 COMMENT 'A 级下限（含）',
  grade_b_min  DECIMAL(10,2) NOT NULL DEFAULT 75 COMMENT 'B 级下限（含）',
  grade_c_min  DECIMAL(10,2) NOT NULL DEFAULT 60 COMMENT 'C 级下限（含），以下为 D',
  update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操行分规则（全校一套，单行）';

-- 默认规则种子（仅空表时插入）：基础 100，A≥90 / B≥75 / C≥60 / D<60
INSERT INTO t_conduct_rule (base_score, grade_a_min, grade_b_min, grade_c_min)
SELECT 100, 90, 75, 60 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_conduct_rule);

CREATE TABLE IF NOT EXISTS t_conduct_account (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id  BIGINT NOT NULL,
  term_id     BIGINT NOT NULL,
  balance     DECIMAL(10,2) NOT NULL COMMENT '当前操行分=基础分+Σ事件增减',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_student_term (student_id, term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操行分余额（每生每学期一行）';

CREATE TABLE IF NOT EXISTS t_conduct_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id  BIGINT NOT NULL,
  term_id     BIGINT NOT NULL,
  source_type VARCHAR(16) NOT NULL COMMENT '评价/活动/荣誉/手动',
  source_id   BIGINT DEFAULT NULL,
  delta       DECIMAL(10,2) NOT NULL COMMENT '本次增减（正/负）',
  reason      VARCHAR(255) NOT NULL COMMENT '事由（评价=格-指标名；手动必填）',
  operator_id BIGINT DEFAULT NULL COMMENT '操作人（事件联动=评价教师）',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_student_term (student_id, term_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操行分流水';

CREATE TABLE IF NOT EXISTS t_shop_item (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(64) NOT NULL COMMENT '商品名（免作业券/文具盲盒…）',
  price_coin  DECIMAL(10,2) NOT NULL COMMENT '兑换价（能量币）',
  stock       INT NOT NULL DEFAULT -1 COMMENT '库存（-1=不限）',
  sort        INT NOT NULL DEFAULT 0,
  status      TINYINT NOT NULL DEFAULT 1 COMMENT '1 上架/0 下架',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成长银行商品';

-- 兑换流水落既有 t_coin_expense（表已建零代码，激活；item 记商品名快照）
ALTER TABLE t_coin_expense
  ADD COLUMN item_id BIGINT NULL COMMENT '关联 t_shop_item.id（手动录入流水为 NULL）',
  ADD COLUMN operator_id BIGINT NULL COMMENT '兑换录入教师';
