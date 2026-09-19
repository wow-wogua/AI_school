-- V14（2026-09-19）：管理员/领导账号双人审批流（批2-5）
-- 新建 ADMIN/LEADER 或教师原地升级为 ADMIN/LEADER 须另一名管理员/领导审批；
-- 拒绝=新号删除/升级不动（自动还原）。审批动作经 AuditFilter 留痕。
CREATE TABLE IF NOT EXISTS t_role_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL COMMENT '目标账号（新建=新号本身，升级=教师本人）',
  req_type VARCHAR(20) NOT NULL COMMENT 'CREATE=新建账号 / UPGRADE=原地升级',
  target_role VARCHAR(20) NOT NULL COMMENT '目标角色 ADMIN/LEADER',
  from_role VARCHAR(20) NULL COMMENT '升级前角色（CREATE 为空）',
  requested_by BIGINT NOT NULL COMMENT '发起人 user_id',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  handled_by BIGINT NULL COMMENT '审批人 user_id',
  handle_time DATETIME NULL COMMENT '审批时间',
  handle_note VARCHAR(200) NULL COMMENT '拒绝/通过备注',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  KEY idx_status (status, create_time),
  KEY idx_user (user_id)
) COMMENT='管理员/领导账号双人审批（批2-5）';
