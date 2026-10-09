-- 批43① 采购申请（OA 第五类型，钉钉流程移植）：五级固定链 部门负责人→库存确认→主管校领导→招采中心确认→采购验收
-- 每级配置值为逗号分隔 user_id（多人=或签，任一人通过即过级；单人配置与既有四类型兼容）
-- oa_purchase_cc=终态抄送人（管理员预设不可删，同钉钉「抄送人」节点）
INSERT INTO t_sys_config (cfg_key, cfg_value) VALUES
  ('oa_purchase_l1', ''), ('oa_purchase_l2', ''), ('oa_purchase_l3', ''),
  ('oa_purchase_l4', ''), ('oa_purchase_l5', ''), ('oa_purchase_cc', '')
ON DUPLICATE KEY UPDATE cfg_key = cfg_key;
