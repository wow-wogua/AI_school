-- 批42：成长报告分期归档——期中/期末标签，往期全量留存
-- period 只对 TERM 学期报告有意义；YEAR 学年 / SCHOOL 在校报告恒 NULL（前端不展示期次）
ALTER TABLE t_report ADD COLUMN period VARCHAR(8) NULL COMMENT '期次：MID=期中 FINAL=期末；NULL 视为期末（存量/学年/在校兼容）';

-- 存量回填：生成时间落在该学期前半=期中、后半=期末；无生成时间/学期区间不全=期末
UPDATE t_report r JOIN t_term t ON r.term_id = t.id
SET r.period = CASE WHEN r.gen_time IS NOT NULL
        AND t.start_date IS NOT NULL AND t.end_date IS NOT NULL
        AND r.gen_time < t.start_date + INTERVAL FLOOR(DATEDIFF(t.end_date, t.start_date) / 2) DAY
    THEN 'MID' ELSE 'FINAL' END
WHERE r.period IS NULL;
