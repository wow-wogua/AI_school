-- App 在线升级：安卓安装包版本发布记录（管理端「版本更新」页签上传，老师端 App 弹窗升级）
CREATE TABLE IF NOT EXISTS t_app_release (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  version_code INT          NOT NULL COMMENT '安卓 versionCode（递增整数，App 端与此比对判定新版）',
  version_name VARCHAR(32)  NOT NULL COMMENT '版本名（如 1.0.15）',
  notes        VARCHAR(2000)         DEFAULT NULL COMMENT '更新说明（换行分隔，弹窗逐条展示）',
  file_url     VARCHAR(255) NOT NULL COMMENT 'MinIO 对象名（app/release-{versionCode}.apk）',
  file_size    BIGINT       NOT NULL COMMENT '安装包字节数（进度条总大小）',
  force_flag   TINYINT      NOT NULL DEFAULT 0 COMMENT '强制更新：1=弹窗不可跳过',
  created_by   BIGINT                DEFAULT NULL COMMENT '上传人 userId',
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 版本发布';
