-- 批12 家长荣誉上传（原始需求一：荣誉证书对所有老师、家长公开，可上传、可看全校）
-- source=TEACHER（存量默认，此前仅班主任/管理员可传）| PARENT（家长自助上传，待班主任确认后上墙）
ALTER TABLE t_honor ADD COLUMN source VARCHAR(8) NOT NULL DEFAULT 'TEACHER' AFTER ai_parsed;
