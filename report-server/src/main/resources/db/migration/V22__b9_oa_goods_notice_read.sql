-- 批9 OA 审批引擎：公章申请（固定三级审批）+ 物资申领（级数可配）+ 通知已读回执
-- 原始需求二·行政审批：核心=「只有一个公章的申请」，节点名用「一级/二级/三级审批」；
-- 物资申领：精细化记录「谁、什么时候、在哪里、拿走了什么」（出库流水行完整承载）。

CREATE TABLE IF NOT EXISTS t_oa_form (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  form_type    VARCHAR(20)  NOT NULL COMMENT 'SEAL=公章使用申请 / GOODS=物资申领',
  title        VARCHAR(200) NOT NULL COMMENT '摘要（列表展示）',
  detail       TEXT         NOT NULL COMMENT '类型专属明细 JSON（SEAL:{reason,useDate} GOODS:[{goodsId,name,qty,unit,location}]）',
  applicant_id BIGINT       NOT NULL COMMENT '申请人 →t_user',
  status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/REVOKED',
  current_level TINYINT     NOT NULL DEFAULT 1 COMMENT '当前待审级（1 起；APPROVED 后=末级）',
  finish_time  DATETIME              COMMENT '终态时间（通过/驳回/撤回）',
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_applicant (applicant_id, status),
  KEY idx_type_status (form_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OA 审批单（批9）';

CREATE TABLE IF NOT EXISTS t_oa_flow_log (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  form_id      BIGINT      NOT NULL COMMENT '→t_oa_form',
  action       VARCHAR(20) NOT NULL COMMENT 'SUBMIT/AGREE/REJECT/REVOKE',
  level        TINYINT     NOT NULL COMMENT '动作发生时所处审批级（SUBMIT=0）',
  node_name    VARCHAR(20) NOT NULL COMMENT '提交/一级审批/二级审批/三级审批',
  operator_id  BIGINT      NOT NULL COMMENT '动作人 →t_user',
  note         VARCHAR(200)          COMMENT '审批意见/撤回原因',
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_form (form_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OA 流转日志（批9）';

-- 物资字典：管理端 CRUD（校方自助维护），申领通过后原子扣减
CREATE TABLE IF NOT EXISTS t_goods (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(100) NOT NULL COMMENT '物资名称',
  unit        VARCHAR(10)  NOT NULL DEFAULT '件' COMMENT '计量单位',
  stock       INT          NOT NULL DEFAULT 0 COMMENT '当前库存（≥0）',
  location    VARCHAR(100)          COMMENT '存放地点（出库时快照进流水=「在哪里拿走」）',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=可申领 0=停用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物资字典（批9）';

-- 出入库流水：IN=管理端入库；OUT=申领审批通过自动出库（applicant=「谁」、location=「在哪里」、goods/qty=「拿走了什么」）
CREATE TABLE IF NOT EXISTS t_goods_flow (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  goods_id     BIGINT      NOT NULL COMMENT '→t_goods',
  goods_name   VARCHAR(100) NOT NULL COMMENT '名称快照',
  qty          INT         NOT NULL COMMENT '数量（正数，方向看 direction）',
  direction    VARCHAR(5)  NOT NULL COMMENT 'IN/OUT',
  location     VARCHAR(100)          COMMENT '地点快照',
  form_id      BIGINT               COMMENT '关联申领单（IN 为空）',
  applicant_id BIGINT               COMMENT 'OUT=申领人 →t_user',
  operator_id  BIGINT      NOT NULL COMMENT '经手人（IN=管理员，OUT=末级审批人）',
  note         VARCHAR(200)          COMMENT '备注（入库原因等）',
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_goods (goods_id),
  KEY idx_form (form_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物资出入库流水（批9）';

-- 通知已读回执：详情打开即打点，uk 幂等；管理端统计已读率+未读名单
CREATE TABLE IF NOT EXISTS t_notice_read (
  notice_id BIGINT      NOT NULL COMMENT '→t_content_item(type=NOTICE)',
  user_id   BIGINT      NOT NULL COMMENT '阅读人 →t_user',
  read_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_notice_user (notice_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知已读回执（批9）';

-- 审批人配置（校方自助）：公章固定三级各配一人；物资级数可配（默认1，最多3），l1-l3 按级数取用
INSERT INTO t_sys_config (cfg_key, cfg_value) VALUES
  ('oa_seal_l1', ''), ('oa_seal_l2', ''), ('oa_seal_l3', ''),
  ('oa_goods_levels', '1'), ('oa_goods_l1', ''), ('oa_goods_l2', ''), ('oa_goods_l3', '')
ON DUPLICATE KEY UPDATE cfg_value = cfg_value;
