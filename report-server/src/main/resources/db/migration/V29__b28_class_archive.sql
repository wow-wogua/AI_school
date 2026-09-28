-- 批28：文件归档——班级导出留痕（导出过才允许清理学生照片，防误删未归档数据）
ALTER TABLE t_class ADD COLUMN archived_time DATETIME NULL;
