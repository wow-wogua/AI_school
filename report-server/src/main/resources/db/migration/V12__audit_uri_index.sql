-- V12（2026-09-19）：审计表 uri 覆盖索引——领导端「教师使用情况」登录次数聚合（uri+时间窗口 GROUP BY user_id）；
-- 家长端上量后 t_audit_log 日增万行（登录审计），无此索引时按 idx_time 扫大范围再过滤 uri，365 天档会退化为近全表扫
ALTER TABLE t_audit_log ADD INDEX idx_uri_time (uri, create_time, user_id);
