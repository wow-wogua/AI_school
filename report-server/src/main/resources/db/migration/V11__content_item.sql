-- V11（2026-09-19）：批2 内容模块——通知公告+育儿课堂（管理端自助配置发布，家长端按班级可见）
CREATE TABLE IF NOT EXISTS t_content_item (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  type         VARCHAR(20)  NOT NULL COMMENT 'NOTICE=通知公告 / PARENTING=育儿课堂',
  title        VARCHAR(200) NOT NULL COMMENT '标题',
  cover_url    VARCHAR(500)          COMMENT '封面图 MinIO objectName（可选）',
  video_url    VARCHAR(500)          COMMENT '外链视频地址（育儿课堂可选，第三方平台链接）',
  content      TEXT                  COMMENT '图文正文（纯文本，保留换行）',
  scope        VARCHAR(20)  NOT NULL DEFAULT 'ALL' COMMENT 'ALL=全校 / CLASS=指定班级',
  class_id     BIGINT                COMMENT 'scope=CLASS 时的班级 →t_class',
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '1=已发布 0=未发布/已下架',
  publish_time DATETIME              COMMENT '首次发布时间',
  create_by    BIGINT                COMMENT '创建人 →t_user',
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_type_status (type, status),
  KEY idx_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容发布（通知公告/育儿课堂）';
