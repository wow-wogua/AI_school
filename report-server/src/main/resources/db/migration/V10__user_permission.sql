-- V10（2026-09-19）：账号权限点（角色之外按人叠加授权）。批1 码表：ADMIN_ACCESS=管理员级配置权（授予领导即开放管理端操作）
CREATE TABLE IF NOT EXISTS t_user_permission (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT NOT NULL COMMENT '→t_user',
  perm_code   VARCHAR(50) NOT NULL COMMENT '权限点代码，批1：ADMIN_ACCESS',
  granted_by  BIGINT DEFAULT NULL COMMENT '授权人 →t_user',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_perm (user_id, perm_code),
  KEY idx_perm (perm_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号权限点';
