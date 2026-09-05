-- 评价聚合链并发丢增量修复（k6 压测实测 + 本地 30 并发复现丢 80%，见 local/loadtest/压测报告.md）：
-- ① 5 张聚合表按自然键防御性去重（加性字段 SUM 并入保留行=最小 id；种子数据已核实零重复，此步为 no-op 兜底）
-- ② 建自然键唯一索引，写入侧同步改 INSERT ... ON DUPLICATE KEY UPDATE 原子累加
--    （EvaluationService 六处 select-then-update 已改造；t_coin_account.student_id 建表即 UNIQUE 免处理）
-- ③ 原非唯一 KEY 与新唯一键前缀相同/被其包含 → 冗余，一并 DROP
-- 注：deploy/schema.sql 按惯例不回写（V2+ 从不回写；新库链路 = schema.sql → baseline 1 → V2+ 依序）

-- ① t_grid_stat_term：九格学期聚合
UPDATE t_grid_stat_term t
JOIN (SELECT MIN(id) AS keep_id, SUM(points) AS points, SUM(eval_count) AS eval_count,
             SUM(score) AS score, MAX(kind_count) AS kind_count
      FROM t_grid_stat_term GROUP BY student_id, term_id, grid_id HAVING COUNT(*) > 1) agg
  ON t.id = agg.keep_id
SET t.points = agg.points, t.eval_count = agg.eval_count, t.score = agg.score, t.kind_count = agg.kind_count;
DELETE d FROM t_grid_stat_term d JOIN t_grid_stat_term k
  ON k.student_id = d.student_id AND k.term_id = d.term_id AND k.grid_id = d.grid_id AND k.id < d.id;
ALTER TABLE t_grid_stat_term
  DROP INDEX idx_student_term,
  ADD UNIQUE KEY uk_student_term_grid (student_id, term_id, grid_id);

-- ② t_grid_stat_week：九格周聚合
UPDATE t_grid_stat_week t
JOIN (SELECT MIN(id) AS keep_id, SUM(score) AS score
      FROM t_grid_stat_week GROUP BY student_id, term_id, grid_id, week_no HAVING COUNT(*) > 1) agg
  ON t.id = agg.keep_id
SET t.score = agg.score;
DELETE d FROM t_grid_stat_week d JOIN t_grid_stat_week k
  ON k.student_id = d.student_id AND k.term_id = d.term_id AND k.grid_id = d.grid_id
 AND k.week_no = d.week_no AND k.id < d.id;
ALTER TABLE t_grid_stat_week
  DROP INDEX idx_student_term,
  ADD UNIQUE KEY uk_student_term_grid_week (student_id, term_id, grid_id, week_no);

-- ③ t_coin_week：能量币周收支
UPDATE t_coin_week t
JOIN (SELECT MIN(id) AS keep_id, SUM(in_mine) AS in_mine, SUM(in_class) AS in_class,
             SUM(in_grade) AS in_grade, SUM(out_mine) AS out_mine,
             SUM(out_class) AS out_class, SUM(out_grade) AS out_grade
      FROM t_coin_week GROUP BY student_id, term_id, week_no HAVING COUNT(*) > 1) agg
  ON t.id = agg.keep_id
SET t.in_mine = agg.in_mine, t.in_class = agg.in_class, t.in_grade = agg.in_grade,
    t.out_mine = agg.out_mine, t.out_class = agg.out_class, t.out_grade = agg.out_grade;
DELETE d FROM t_coin_week d JOIN t_coin_week k
  ON k.student_id = d.student_id AND k.term_id = d.term_id AND k.week_no = d.week_no AND k.id < d.id;
ALTER TABLE t_coin_week
  DROP INDEX idx_student_term,
  ADD UNIQUE KEY uk_student_term_week (student_id, term_id, week_no);

-- ④ t_class_grid_avg：班级九格均值
UPDATE t_class_grid_avg t
JOIN (SELECT MIN(id) AS keep_id, SUM(avg_score) AS avg_score
      FROM t_class_grid_avg GROUP BY class_id, term_id, grid_id HAVING COUNT(*) > 1) agg
  ON t.id = agg.keep_id
SET t.avg_score = agg.avg_score;
DELETE d FROM t_class_grid_avg d JOIN t_class_grid_avg k
  ON k.class_id = d.class_id AND k.term_id = d.term_id AND k.grid_id = d.grid_id AND k.id < d.id;
ALTER TABLE t_class_grid_avg
  DROP INDEX idx_class_term,
  ADD UNIQUE KEY uk_class_term_grid (class_id, term_id, grid_id);

-- ⑤ t_grade_grid_avg：年级九格均值
UPDATE t_grade_grid_avg t
JOIN (SELECT MIN(id) AS keep_id, SUM(avg_score) AS avg_score
      FROM t_grade_grid_avg GROUP BY grade_id, term_id, grid_id HAVING COUNT(*) > 1) agg
  ON t.id = agg.keep_id
SET t.avg_score = agg.avg_score;
DELETE d FROM t_grade_grid_avg d JOIN t_grade_grid_avg k
  ON k.grade_id = d.grade_id AND k.term_id = d.term_id AND k.grid_id = d.grid_id AND k.id < d.id;
ALTER TABLE t_grade_grid_avg
  DROP INDEX idx_grade_term,
  ADD UNIQUE KEY uk_grade_term_grid (grade_id, term_id, grid_id);
