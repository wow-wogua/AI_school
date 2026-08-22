# -*- coding: utf-8 -*-
"""生成 deploy/seed.sql：golden_student.json → 库表行（陈小华精确逆映射）+ 51 个缩放变体学生。

逆映射是 ReportDataBuilder 的严格逆：种入 → 聚合 → 与 golden 逐字段相等（契约零漂移）。
变体用 gen_variants.py 同款 walk 缩放（0.825~1.075），仅供批量渲染压测，不做契约校验。

用法（在 report-server 下）：
  PYTHONIOENCODING=utf-8 python scripts/seed_db.py
  # 再导入：docker exec -i aischool-mysql mysql -uroot -paischool123 ai_school < ../deploy/seed.sql
"""
import copy
import json
import os
import re
from datetime import datetime, timedelta

import bcrypt

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)                       # report-server/
GOLDEN = os.path.join(ROOT, '..', 'report-renderer', 'src', 'main', 'resources', 'golden_student.json')
SCHEMA = os.path.join(ROOT, '..', 'deploy', 'schema.sql')
OUT = os.path.join(ROOT, '..', 'deploy', 'seed.sql')

TERM_CUR, TERM_PREV = 2, 1
CLASS1, CLASS2, GRADE1 = 1, 2, 1
EXAM1 = 1

# 学科：id, 显示名, 短名, 学科页序(subjectPages), 平时成绩序(regularScores)
SUBJECTS = [
    (1, '语文', None, 1, 1), (2, '数学', None, 2, 2), (3, '英语', None, 3, 3),
    (4, '道德与法治', '政治', 8, 4), (5, '历史', None, 4, 5), (6, '地理', None, 6, 6),
    (7, '生物', None, 7, 7), (8, '物理', None, 5, 8), (9, '体育', None, 12, 9),
    (10, '音乐', None, 10, 10), (11, '信息技术', '信息', 9, 11), (12, '美术', None, 11, 12),
]

# 登记教师（real_name → t_user id 3..15）
REGISTRANTS = ['高志强', '崔万印', '廖彧', '肖金梅', '陈楚君', '毛远焕', '方昌强',
               '李振坚', '黎雅', '黎敏仪', '邱思琦', '夏青', '邢维高']
USER_ADMIN, USER_HT1, USER_HT2, USER_TEACH2 = 1, 2, 16, 17
USER_T0 = 3

VARIANT_NAMES = [
    '林晓东', '王雨桐', '张子轩', '李思远', '刘欣怡', '黄浩然', '陈佳琪', '赵一鸣', '周芷若', '吴宇森',
    '郑博文', '何晓岚', '许晋鹏', '冯梓萱', '马嘉树', '沈书瑶', '韩明轩', '曹语彤', '邓子睿', '曾若溪',
    '袁浩宇', '蔡欣妍', '蒋文昊', '余思颖', '杜宇轩', '叶梓涵', '程子墨', '苏雨萱', '魏子涵', '吕明睿',
    '丁一诺', '任嘉懿', '段景行', '雷雨桐', '侯俊杰', '范承宇', '石佳琳', '顾晨曦', '龚子安', '严语汐',
    '钟明玥', '汪子昂', '邵雨桐', '秦子涵', '龙浩然', '常语晨', '万思远', '卢欣然', '郭梓宸', '洪雨欣',
    '聂远航',
]

FIXED_KEYS = {'rate', 'level'}
EVAL_T0 = datetime(2026, 2, 16, 8, 0, 0)


def scale_num(v, f):
    if isinstance(v, bool) or not isinstance(v, (int, float)):
        return v
    if isinstance(v, int):
        return max(0, round(v * f))
    return round(v * f, 1)


def walk(o, f):
    if isinstance(o, dict):
        return {k: (v if k in FIXED_KEYS else walk(v, f)) for k, v in o.items()}
    if isinstance(o, list):
        return [walk(x, f) for x in o]
    return scale_num(o, f)


def sq(v):
    """SQL 字面量。"""
    if v is None:
        return 'NULL'
    if isinstance(v, bool):
        return '1' if v else '0'
    if isinstance(v, (int, float)):
        return str(v)
    if isinstance(v, dict):
        return sq(json.dumps(v, ensure_ascii=False, separators=(',', ':')))
    return "'" + str(v).replace('\\', '\\\\').replace("'", "''") + "'"


def ins(table, cols, rows):
    if not rows:
        return ''
    vals = ',\n'.join('(' + ', '.join(sq(v) for v in r) + ')' for r in rows)
    return 'INSERT INTO %s (%s) VALUES\n%s;\n' % (table, ', '.join(cols), vals)


def parse_hw(value):
    """'77(42次)' → (77, 42)；'-' → None"""
    m = re.match(r'^(-?\d+)\((\d+)次\)$', value)
    return (int(m.group(1)), int(m.group(2))) if m else None


def split_score(total, n):
    """组内分摊：和精确等于 total（整数记录分值）。"""
    base = int(total / n)
    return [total - base * (n - 1)] + [base] * (n - 1)


def build(g, sid, ctx):
    """golden/变体 JSON → 各表行。ctx: subj_by_name / subj_by_short / grid_id / ind_id / registrant_id"""
    rows = []
    for s in g['academic']['subjects']:
        rows.append(('t_score', (EXAM1, ctx['subj_by_name'][s['name']], sid, s['score'], None, None)))
    rows.append(('t_student_analysis', (sid, TERM_CUR, g['academic']['advantage'], g['academic']['toImprove'],
                                        json.dumps(g['radar']['advantages'], ensure_ascii=False),
                                        json.dumps(g['radar']['toImprove'], ensure_ascii=False))))
    p = g['academic']['process']
    rows.append(('t_process_stat', (sid, TERM_CUR,
                                    p['positive']['mine'], p['positive']['classAvg'], p['positive']['gradeAvg'],
                                    p['negative']['mine'], p['negative']['classAvg'], p['negative']['gradeAvg'])))
    for w, (m, c, a) in enumerate(zip(p['weekly']['mine'], p['weekly']['classAvg'], p['weekly']['gradeAvg']), 1):
        rows.append(('t_process_week', (sid, TERM_CUR, w, m, c, a)))

    for page in g['subjectPages']:
        subj = ctx['subj_by_name'][page['name']]
        rows.append(('t_subject_stat_term', (sid, TERM_CUR, subj,
                                             page['pos']['mine'], page['pos']['classAvg'], page['pos']['gradeAvg'],
                                             page['neg']['mine'], page['neg']['classAvg'], page['neg']['gradeAvg'])))
        for w, (m, c, a) in enumerate(zip(page['weekly']['mine'], page['weekly']['classAvg'],
                                          page['weekly']['gradeAvg']), 1):
            rows.append(('t_subject_stat_week', (sid, TERM_CUR, subj, w, m, c, a)))

    for s in g['regularScores']['subjects']:
        subj = ctx['subj_by_short'].get(s['name'], ctx['subj_by_name'].get(s['name']))
        rows.append(('t_regular_score', (sid, subj, TERM_CUR, s['score'])))
    for row in g['regularScores']['homework']['rows']:
        subj = ctx['subj_by_short'].get(row['subject'], ctx['subj_by_name'].get(row['subject']))
        if all(v == '-' for v in row['values']):
            # 全 '-' 行（样例政治）：纳入登记但无记录 → 5 格 times=0 占位（聚合输出 '-'）
            for col in range(5):
                rows.append(('t_homework_stat', (sid, TERM_CUR, subj, col, 0, 0)))
            continue
        for col, value in enumerate(row['values']):
            if value != '-':
                sc, times = parse_hw(value)
                rows.append(('t_homework_stat', (sid, TERM_CUR, subj, col, sc, times)))

    t = EVAL_T0
    for grid in g['grids']:
        gi = ctx['grid_id'][grid['name']]
        rows.append(('t_grid_stat_term', (sid, TERM_CUR, gi, grid['points'], grid['count'],
                                          grid['kinds'], grid['cur']['mine'])))
        rows.append(('t_grid_stat_term', (sid, TERM_PREV, gi, grid['points'], grid['count'],
                                          grid['kinds'], grid['prev']['mine'])))
        for w, m in enumerate(grid['weekly']['mine'], 1):
            rows.append(('t_grid_stat_week', (sid, TERM_CUR, gi, w, m)))
        for rec in grid['records']:
            names = rec['registrants'].split('、')
            for nm, part in zip(names, split_score(int(rec['score']), len(names))):
                rows.append(('t_evaluation', (sid, ctx['registrant_id'][nm], ctx['ind_id'][(grid['name'], rec['_ind'])],
                                              rec['title'], part, t.strftime('%Y-%m-%d %H:%M:%S'))))
            t += timedelta(minutes=10)

    c = g['coin']
    rows.append(('t_coin_account', (sid, c['current'], c['total'])))
    seen = set()
    for i, e in enumerate(c['incomeTop5'] + c['incomeLeast3'], 1):
        if e['name'] in seen:
            continue
        seen.add(e['name'])                       # '-'（coin=NULL 缺位模块）照常入库
        rows.append(('t_coin_income', (sid, TERM_CUR, '评价', None, e['name'], e['value'], e['value'], i,
                                       '2026-06-30 10:00:00')))
    for e in c['dailyTop10']:
        rows.append(('t_coin_expense', (sid, TERM_CUR, e['item'],
                                        int(e['amount'].replace('能量币', '')), e['date'] + ' 12:00:00')))
    for w, (im, ic, ig, om, oc, og) in enumerate(zip(
            c['weeklyIncome']['mine'], c['weeklyIncome']['classAvg'], c['weeklyIncome']['gradeAvg'],
            c['weeklyExpense']['mine'], c['weeklyExpense']['classAvg'], c['weeklyExpense']['gradeAvg']), 1):
        rows.append(('t_coin_week', (sid, TERM_CUR, w, im, ic, ig, om, oc, og)))
    rows.append(('t_coin_stat', (sid, TERM_CUR,
                                 json.dumps(c['compare']['classAvg'], ensure_ascii=False),
                                 json.dumps(c['compare']['gradeAvg'], ensure_ascii=False))))
    rows.append(('t_growth_symbol_stat', (sid, TERM_CUR, g['growthSymbol']['score'])))
    comp = {d['name']: d['level'] for d in g['comprehensive']['dims']}
    rows.append(('t_comprehensive', (sid, TERM_CUR, comp['思想品德'], comp['学业水平'], comp['身心健康'],
                                     comp['艺术素养'], comp['社会实践'], g['comprehensive']['finalLevel'])))
    rows.append(('t_comment', (sid, TERM_CUR, '班主任', g['headTeacherComment'], None, '已确认')))
    return rows


def main():
    with open(GOLDEN, encoding='utf-8') as fp:
        golden = json.load(fp)

    subj_by_name = {n: i for i, n, *_ in SUBJECTS}
    subj_by_short = {s: i for i, n, s, *_ in SUBJECTS if s}
    grid_id = {g['name']: i for i, g in enumerate(golden['grids'], 1)}
    registrant_id = {nm: USER_T0 + i for i, nm in enumerate(REGISTRANTS)}

    # 指标：记录卡 subtitle「国格-爱国精神 / 信格-」反推；记录卡挂 _ind 供 evaluation 引用
    ind_rows, ind_id, ind_map = [], 1, {}
    for grid in golden['grids']:
        for rec in grid['records']:
            sub = rec['subtitle'].split('-', 1)[1] if '-' in rec['subtitle'] else ''
            rec['_ind'] = sub
            if (grid['name'], sub) not in ind_map:
                ind_map[(grid['name'], sub)] = ind_id
                ind_rows.append((ind_id, grid_id[grid['name']], sub, '负' if rec['score'].startswith('-') else '正', 1))
                ind_id += 1
    ctx = {'subj_by_name': subj_by_name, 'subj_by_short': subj_by_short, 'grid_id': grid_id,
           'ind_id': ind_map, 'registrant_id': registrant_id}

    out = ['-- AI_school 种子数据（seed_db.py 生成；陈小华 = golden 精确逆映射 + 51 变体学生）\n',
           'SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n']
    for ln in open(SCHEMA, encoding='utf-8'):
        if ln.strip().startswith('CREATE TABLE'):
            out.append('TRUNCATE %s;\n' % ln.split()[5])
    out.append('SET FOREIGN_KEY_CHECKS=1;\n\n')

    pwd_admin = bcrypt.hashpw(b'admin123', bcrypt.gensalt(10)).decode()
    pwd_teacher = bcrypt.hashpw(b'aischool123', bcrypt.gensalt(10)).decode()
    users = [(USER_ADMIN, 'admin', pwd_admin, '系统管理员', 'ADMIN'),
             (USER_HT1, 'litao', pwd_teacher, golden['student']['headTeacher'], 'HEAD_TEACHER'),
             (USER_HT2, 'zhaolaoshi', pwd_teacher, '赵老师', 'HEAD_TEACHER'),
             (USER_TEACH2, 'wanglaoshi', pwd_teacher, '王老师', 'TEACHER')]
    users += [(USER_T0 + i, 't%02d' % i, pwd_teacher, nm, 'TEACHER') for i, nm in enumerate(REGISTRANTS)]
    out.append(ins('t_user', ('id', 'username', 'password_hash', 'real_name', 'role'), users))
    out.append(ins('t_grade', ('id', 'name', 'school_year'),
                   [(GRADE1, golden['student']['grade'], '2025-2026')]))
    out.append(ins('t_class', ('id', 'grade_id', 'name', 'head_teacher_id'),
                   [(CLASS1, GRADE1, golden['student']['clazz'], USER_HT1),
                    (CLASS2, GRADE1, '初一(2)班', USER_HT2)]))
    out.append(ins('t_term', ('id', 'name', 'start_date', 'end_date', 'is_current'),
                   [(TERM_PREV, '2025年秋季学期', '2025-09-01', '2026-01-20', 0),
                    (TERM_CUR, golden['school']['term'], '2026-02-09', '2026-06-30', 1)]))
    out.append(ins('t_teach', ('id', 'teacher_id', 'class_id', 'subject_id'),
                   [(1, USER_TEACH2, CLASS2, 1)]))

    pages = {p['name']: p for p in golden['subjectPages']}
    out.append(ins('t_subject', ('id', 'name', 'short_name', 'type', 'sort', 'regular_sort', 'motto',
                                 'proc_h_min', 'proc_h_max', 'proc_h_step', 'proc_w_max'),
                   [(sid, nm, short, '国家课程', sort, rsort,
                     pages[nm]['motto'] if nm in pages else None,
                     pages[nm]['procH']['min'] if nm in pages else None,
                     pages[nm]['procH']['max'] if nm in pages else None,
                     pages[nm]['procH']['step'] if nm in pages else None,
                     pages[nm]['procW']['max'] if nm in pages else None)
                    for sid, nm, short, sort, rsort in SUBJECTS]))
    out.append(ins('t_exam', ('id', 'term_id', 'name', 'exam_date', 'class_max_total', 'grade_max_total'),
                   [(EXAM1, TERM_CUR, '期末考试', '2026-06-20',
                     golden['academic']['total']['classMax'], golden['academic']['total']['gradeMax'])]))
    out.append(ins('t_exam_subject', ('id', 'exam_id', 'subject_id', 'class_max', 'grade_max'),
                   [(i, EXAM1, subj_by_name[p['name']], p['classMax'], p['gradeMax'])
                    for i, p in enumerate(golden['subjectPages'], 1)]))
    out.append(ins('t_indicator', ('id', 'grid_id', 'name', 'direction', 'default_score'), ind_rows))
    out.append(ins('t_grid', ('id', 'code', 'name', 'icon', 'sort', 'cur_axis_max', 'cur_axis_step',
                              'prev_axis_max', 'prev_axis_step', 'week_min', 'week_max', 'week_step'),
                   [(grid_id[g['name']], 'G%d' % grid_id[g['name']], g['name'], None, grid_id[g['name']],
                     g['cur']['axisMax'], g['cur']['step'], g['prev']['axisMax'], g['prev']['step'],
                     g['weekly']['min'], g['weekly']['max'], g['weekly']['step'])
                    for g in golden['grids']]))

    sections = {
        'intro': golden['school']['intro'],
        'nineGridIntro': golden['school']['nineGridIntro'],
        'philosophy': golden['school']['philosophy'],
        'radar': {k: golden['radar'][k] for k in ('max', 'motto', 'mottoNote', 'mottoSource')},
        'coin': {
            'compare': {k: golden['coin']['compare'][k] for k in ('max', 'step')},
            'weeklyIncome': {k: golden['coin']['weeklyIncome'][k] for k in ('max', 'step')},
            'weeklyExpense': {k: golden['coin']['weeklyExpense'][k] for k in ('max', 'step')},
            'incomeTop': {k: golden['coin']['incomeTopAxis'][k] for k in ('max', 'step')},
            'incomeLeast': {k: golden['coin']['incomeLeastAxis'][k] for k in ('max', 'step')},
            'expenseTop': {k: golden['coin']['expenseTopAxis'][k] for k in ('max', 'step')},
        },
    }
    out.append(ins('t_report_template', ('id', 'school_name', 'sections', 'status'),
                   [(1, golden['school']['name'], sections, '启用')]))
    out.append(ins('t_coin_rate', ('id', 'rate', 'effective_date'),
                   [(1, golden['coin']['rate'],
                     '2026-%s-%s' % (golden['coin']['rateMonth'], golden['coin']['rateDay']))]))
    next_min = golden['growthSymbol']['score'] + golden['growthSymbol']['toNext']
    out.append(ins('t_growth_level', ('id', 'level', 'min_score', 'symbol_name'),
                   [(1, 1, 0, '种子'), (2, 2, 500, '嫩芽'), (3, 3, 1000, '小树'),
                    (4, 4, next_min, '大树'), (5, 5, round(next_min + 400), '栋梁')]))

    # 班级/年级九格均值（两班同值；变体个人数据不回写均值表，不参与契约校验）
    class_avg, grade_avg = [], []
    for cls in (CLASS1, CLASS2):
        for term, key in ((TERM_CUR, 'cur'), (TERM_PREV, 'prev')):
            for g in golden['grids']:
                class_avg.append((cls, term, grid_id[g['name']], g[key]['classAvg']))
    for term, key in ((TERM_CUR, 'cur'), (TERM_PREV, 'prev')):
        for g in golden['grids']:
            grade_avg.append((GRADE1, term, grid_id[g['name']], g[key]['gradeAvg']))
    out.append(ins('t_class_grid_avg', ('class_id', 'term_id', 'grid_id', 'avg_score'), class_avg))
    out.append(ins('t_grade_grid_avg', ('grade_id', 'term_id', 'grid_id', 'avg_score'), grade_avg))

    # 学生：陈小华(id=1, class1) + 51 变体（前 49 入 class1 凑满 50 人；后 2 入 class2 供权限测试）
    students = [(1, golden['student']['studentNo'], golden['student']['name'], '男', CLASS1, '2023-09-01')]
    data_rows = build(golden, 1, ctx)
    for i, nm in enumerate(VARIANT_NAMES, 1):
        sid = 1 + i
        students.append((sid, '2023%04d' % (i + 1), nm, '女' if i % 2 else '男',
                         CLASS1 if i <= 49 else CLASS2, '2023-09-01'))
        data = walk(copy.deepcopy(golden), 0.82 + i * 0.005)
        data_rows += build(data, sid, ctx)
    out.append(ins('t_student', ('id', 'student_no', 'name', 'gender', 'class_id', 'enroll_date'), students))

    COLS = {
        't_score': ('exam_id', 'subject_id', 'student_id', 'score', 'class_rank', 'grade_rank'),
        't_student_analysis': ('student_id', 'term_id', 'advantage', 'to_improve',
                               'radar_advantages', 'radar_to_improve'),
        't_process_stat': ('student_id', 'term_id', 'pos_mine', 'pos_class_avg', 'pos_grade_avg',
                           'neg_mine', 'neg_class_avg', 'neg_grade_avg'),
        't_process_week': ('student_id', 'term_id', 'week_no', 'mine', 'class_avg', 'grade_avg'),
        't_subject_stat_term': ('student_id', 'term_id', 'subject_id', 'pos_mine', 'pos_class_avg',
                                'pos_grade_avg', 'neg_mine', 'neg_class_avg', 'neg_grade_avg'),
        't_subject_stat_week': ('student_id', 'term_id', 'subject_id', 'week_no',
                                'mine', 'class_avg', 'grade_avg'),
        't_regular_score': ('student_id', 'subject_id', 'term_id', 'score'),
        't_homework_stat': ('student_id', 'term_id', 'subject_id', 'col_type', 'score', 'times'),
        't_grid_stat_term': ('student_id', 'term_id', 'grid_id', 'points', 'eval_count', 'kind_count', 'score'),
        't_grid_stat_week': ('student_id', 'term_id', 'grid_id', 'week_no', 'score'),
        't_evaluation': ('student_id', 'teacher_id', 'indicator_id', 'title', 'score', 'eval_time'),
        't_coin_account': ('student_id', 'current_coin', 'total_coin'),
        't_coin_income': ('student_id', 'term_id', 'source_type', 'source_id', 'module',
                          'score', 'coin', 'display_order', 'create_time'),
        't_coin_expense': ('student_id', 'term_id', 'item', 'coin', 'create_time'),
        't_coin_week': ('student_id', 'term_id', 'week_no', 'in_mine', 'in_class', 'in_grade',
                        'out_mine', 'out_class', 'out_grade'),
        't_coin_stat': ('student_id', 'term_id', 'compare_class_avg', 'compare_grade_avg'),
        't_growth_symbol_stat': ('student_id', 'term_id', 'score'),
        't_comprehensive': ('student_id', 'term_id', 'moral', 'ability', 'health',
                            'aesthetic', 'practice', 'final_level'),
        't_comment': ('student_id', 'term_id', 'type', 'content', 'ai_draft', 'status'),
    }
    by_table = {}
    for table, row in data_rows:
        by_table.setdefault(table, []).append(row)
    for table, cols in COLS.items():
        out.append(ins(table, cols, by_table.get(table, [])))

    with open(OUT, 'w', encoding='utf-8') as fp:
        fp.write(''.join(out))
    n_eval = len(by_table.get('t_evaluation', []))
    print('%s  %.1f MB  students=%d eval=%d score=%d' % (
        OUT, os.path.getsize(OUT) / 1048576, len(students), n_eval, len(by_table.get('t_score', []))))
    print('账号：admin/admin123（管理员）litao/aischool123（初一1班班主任）'
          'zhaolaoshi/aischool123（初一2班班主任）wanglaoshi/aischool123（初一2班任课）')


if __name__ == '__main__':
    main()
