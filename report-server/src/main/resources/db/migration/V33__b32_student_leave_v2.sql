-- 批32（2026-10-06）：学生请假流程重构
-- 拍板：①家长不再 App 提交（微信/电话告知班主任，班主任代录），家长只收通知
--      ②按时长分级：≤3天 班主任/生活老师录入即生效；3~7天 +级长审批；7~30天 +学成中心主任终审；>30天 走纸质
--      ③时间粒度到时分，时长自动折算天数；④起止列 DATE→DATETIME（历史行自动 00:00:00）
--      ⑤审批轨迹独立流水表（详情页时间线）
ALTER TABLE t_student_leave
  ADD COLUMN creator_id BIGINT NULL COMMENT '发起教师（班主任/生活老师/级长/主任/领导/管理员代录）' AFTER parent_id,
  ADD COLUMN duration_days DECIMAL(5,1) NULL COMMENT '时长（天，自动折算，分级审批依据）' AFTER reason,
  ADD COLUMN total_step TINYINT NULL COMMENT '审批级数（0=录入即生效 / 1=级长一级 / 2=级长+学成中心主任）' AFTER status,
  ADD COLUMN current_step TINYINT NULL COMMENT '当前待审级（1=待级长 / 2=待主任；终态=0）' AFTER total_step;

ALTER TABLE t_student_leave
  CHANGE COLUMN start_date start_time DATETIME NOT NULL COMMENT '请假开始（含时分）',
  CHANGE COLUMN end_date end_time DATETIME NOT NULL COMMENT '请假结束（含时分）';

-- 历史行（批27~31 家长提交单）回填：时长按天粒度（含当天），视为已走完单级流程
UPDATE t_student_leave
  SET duration_days = DATEDIFF(end_time, start_time) + 1,
      total_step    = 1,
      current_step  = 0
  WHERE duration_days IS NULL;

-- 审批/登记轨迹（详情页时间线数据源）
CREATE TABLE IF NOT EXISTS t_leave_flow_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  leave_id    BIGINT NOT NULL COMMENT '→t_student_leave',
  action      VARCHAR(16) NOT NULL COMMENT 'SUBMIT 提交 / APPROVE 通过 / REJECT 驳回 / CANCEL 撤销 / LEAVE 登记离校 / RETURN 登记返校',
  step        TINYINT DEFAULT NULL COMMENT '审批级（1=级长 2=主任；非审批动作 NULL）',
  operator_id BIGINT NOT NULL COMMENT '操作人 →t_user',
  note        VARCHAR(200) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_lfl_leave (leave_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='请假流转日志（批32）';
