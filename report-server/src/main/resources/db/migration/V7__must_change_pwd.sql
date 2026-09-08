-- 密码策略：管理员设密/重置/批量导入初始密码后，首次登录强制改密（NIST SP 800-63B：管理员已知口令不应长期有效）
ALTER TABLE t_user
  ADD COLUMN must_change_pwd TINYINT NOT NULL DEFAULT 0 COMMENT '首次登录强制改密：1=待改（管理员设密/重置/批量导入时置 1，自助改密成功清 0）';
