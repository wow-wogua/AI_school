-- 批30：文明班 B 案（打分+评选）+ 兑换核销（领取二次确认）+ 谈心随访标记
-- 文明班打分：每行=某班某日一条扣分/加分事件（条目文本快照）；班级当日分=120+当日合计
CREATE TABLE IF NOT EXISTS t_civility_score (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  class_id    BIGINT NOT NULL,
  score_date  DATE NOT NULL,
  section_no  INT NOT NULL COMMENT '1-12 大项（对照德育规范 conductRules）',
  item_text   VARCHAR(300) NOT NULL COMMENT '条目文本快照',
  delta       DECIMAL(6,1) NOT NULL COMMENT '单次分值，负=扣分',
  cnt         INT NOT NULL DEFAULT 1 COMMENT '人次/次数',
  note        VARCHAR(200),
  operator_id BIGINT NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_date (score_date, class_id),
  KEY idx_class (class_id, score_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文明班打分流水（批30）';

-- 文明班评选快照：按月冻结各班排名（重评=覆盖更新）
CREATE TABLE IF NOT EXISTS t_civility_award (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  period_type  VARCHAR(10) NOT NULL COMMENT 'MONTH',
  period_value VARCHAR(10) NOT NULL COMMENT '如 2026-10',
  class_id     BIGINT NOT NULL,
  grade_id     BIGINT NOT NULL,
  rank_no      INT NOT NULL,
  total_score  DECIMAL(9,1) NOT NULL,
  settle_time  DATETIME,
  settle_by    BIGINT,
  UNIQUE KEY uk_period_class (period_type, period_value, class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文明班评选结果（批30）';

-- 兑换核销：0=待领取（新兑换默认），1=已领取（存量历史记录默认 1）
ALTER TABLE t_coin_expense
  ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '0=待领取 1=已领取(核销)',
  ADD COLUMN confirm_time DATETIME NULL COMMENT '核销时间',
  ADD COLUMN confirm_by BIGINT NULL COMMENT '核销人';

-- 谈心随访：0=无需随访 1=待随访（到期提醒） 2=已随访
ALTER TABLE t_talk
  ADD COLUMN follow_up TINYINT NOT NULL DEFAULT 0 COMMENT '0=无需 1=待随访 2=已随访',
  ADD COLUMN follow_due DATE NULL COMMENT '随访到期日';

INSERT INTO t_sys_config (cfg_key, cfg_value) VALUES ('talk_follow_days', '14')
ON DUPLICATE KEY UPDATE cfg_value = cfg_value;
