-- 批43：班级整体加减分（素养评价双轨之一：不落具体学生，进文明班评比、不进学生个人档案）
CREATE TABLE t_class_score (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  class_id BIGINT NOT NULL COMMENT '班级',
  score_date DATE NOT NULL COMMENT '记分日期',
  category TINYINT NULL COMMENT '德育规范大项 1-12（可空=不归类）',
  item_text VARCHAR(200) NOT NULL COMMENT '事项说明',
  delta DECIMAL(6,1) NOT NULL COMMENT '加减分（正=加 负=减，非零）',
  note VARCHAR(200) NULL COMMENT '备注',
  operator_id BIGINT NULL COMMENT '记分人',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_class_date (class_id, score_date)
) COMMENT='班级整体加减分（批43）：检查日判定与文明班排名的分源之一';
