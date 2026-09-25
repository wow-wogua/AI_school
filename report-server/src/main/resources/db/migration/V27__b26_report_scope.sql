-- 批26 报告三类：t_report/t_report_task 加 scope（TERM 学期报告=存量默认 / YEAR 学年报告 / SCHOOL 在校报告）
-- term_ids：YEAR=同学年学期 id 逗号串（锚定学期为 termId）；SCHOOL=全部学期（生成时快照，防后续增学期漂移）
ALTER TABLE t_report_task
    ADD COLUMN scope_type VARCHAR(16) NOT NULL DEFAULT 'TERM' COMMENT '报告类型 TERM/YEAR/SCHOOL',
    ADD COLUMN term_ids VARCHAR(64) NULL COMMENT '覆盖学期 id 快照（逗号串；SCHOOL=全部学期）';

ALTER TABLE t_report
    ADD COLUMN scope_type VARCHAR(16) NOT NULL DEFAULT 'TERM' COMMENT '报告类型 TERM/YEAR/SCHOOL',
    ADD COLUMN term_ids VARCHAR(64) NULL COMMENT '覆盖学期 id 快照（逗号串）';
