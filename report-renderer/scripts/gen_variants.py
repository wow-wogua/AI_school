# -*- coding: utf-8 -*-
"""M5 并发验证：从 golden_student.json 派生 10 份不同学生的数据变体。"""
import copy
import json
import os

BASE = 'src/main/resources/golden_student.json'
OUT_DIR = 'target/batch'
NAMES = ['林晓东', '王雨桐', '张子轩', '李思远', '刘欣怡',
         '黄浩然', '陈佳琪', '赵一鸣', '周芷若', '吴宇森']
FIXED_KEYS = {'rate', 'level'}  # 汇率/等级不随成绩缩放


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


def main():
    with open(BASE, encoding='utf-8') as fp:
        base = json.load(fp)
    os.makedirs(OUT_DIR, exist_ok=True)
    for i, name in enumerate(NAMES, 1):
        d = walk(copy.deepcopy(base), 0.82 + i * 0.025)  # 0.845 ~ 1.07
        # recordPageCount 是布局结构值（记录分页数），不随成绩缩放，按记录数重算
        for g in d['grids']:
            g['recordPageCount'] = (len(g['records']) + 6) // 7
        d['student']['name'] = name
        d['student']['studentNo'] = '2023%04d' % i
        out = os.path.join(OUT_DIR, 'student%02d.json' % i)
        with open(out, 'w', encoding='utf-8') as fp:
            json.dump(d, fp, ensure_ascii=False, indent=1)
        print(out)


if __name__ == '__main__':
    main()
