# -*- coding: utf-8 -*-
"""契约零漂移验证（验收①，永久脚本，勿放 /tmp）。

1. 登录取 JWT → GET /api/report/data/1?termId=2（聚合层产出的契约 JSON）
2. 与 golden_student.json 深比对：数值 int/float 视为相等（1020==1020.0，渲染层 JS 等价；
   growthSymbol 已在后端恒为 Double），其余字段须全等；差异逐条打印
3. 聚合 JSON 走渲染核心 → PDF，页数 50 且每页中文大标题序列与 target/report.pdf 一致

用法：PYTHONIOENCODING=utf-8 python scripts/verify_contract.py
"""
import json
import os
import re
import subprocess
import sys
import urllib.request

import fitz  # PyMuPDF

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
API = 'http://localhost:8080'
GOLDEN = os.path.join(ROOT, '..', 'report-renderer', 'src', 'main', 'resources', 'golden_student.json')
TARGET_PDF = os.path.join(ROOT, '..', 'report-renderer', 'target', 'report.pdf')
RENDERER_CP = os.path.join(ROOT, '..', 'report-renderer', 'target')
OUT_DIR = os.path.join(ROOT, 'target', 'contract-check')


def api(path, token=None):
    req = urllib.request.Request(API + path)
    if token:
        req.add_header('Authorization', 'Bearer ' + token)
    with urllib.request.urlopen(req, timeout=30) as resp:
        body = json.load(resp)
    assert body.get('code') == 0, body
    return body['data']


def diff(path, a, b, out):
    """deep compare；数值放宽 int/float（1020==1020.0）。"""
    if isinstance(a, dict) and isinstance(b, dict):
        for k in a.keys() | b.keys():
            if k not in a:
                out.append('%s: 聚合缺失' % (path + '.' + k))
            elif k not in b:
                out.append('%s: golden 无此字段' % (path + '.' + k))
            else:
                diff(path + '.' + k, a[k], b[k], out)
    elif isinstance(a, list) and isinstance(b, list):
        if len(a) != len(b):
            out.append('%s: 长度 %d != %d' % (path, len(a), len(b)))
        for i, (x, y) in enumerate(zip(a, b)):
            diff('%s[%d]' % (path, i), x, y, out)
    elif isinstance(a, bool) or isinstance(b, bool):
        if a != b:
            out.append('%s: %r != %r' % (path, a, b))
    elif isinstance(a, (int, float)) and isinstance(b, (int, float)):
        if a != b:  # bool 已排除；1 == 1.0 数值等价放行
            out.append('%s: %r != %r' % (path, a, b))
    elif a != b:
        out.append('%s: %r != %r' % (path, a, b))


def title_sig(pdf_path):
    doc = fitz.open(pdf_path)
    pages = []
    for pg in doc:
        titles = []
        for b in pg.get_text('dict')['blocks']:
            for ln in b.get('lines', []):
                for s in ln['spans']:
                    if s['size'] >= 12.9 and re.search(r'[一-鿿]', s['text']):
                        titles.append(s['text'])
        pages.append(tuple(titles))
    return len(doc), pages


def main():
    with open(GOLDEN, encoding='utf-8') as fp:
        golden = json.load(fp)

    login = json.dumps({'username': 'admin', 'password': 'admin123'}).encode()
    req = urllib.request.Request(API + '/api/auth/login', data=login,
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=30) as resp:
        token = json.load(resp)['data']['token']

    agg = api('/api/report/data/1?termId=2', token)
    os.makedirs(OUT_DIR, exist_ok=True)
    with open(os.path.join(OUT_DIR, 'agg_student1.json'), 'w', encoding='utf-8') as fp:
        json.dump(agg, fp, ensure_ascii=False, indent=1)

    out = []
    diff('$', agg, golden, out)
    if out:
        print('── 契约差异 %d 处 ──' % len(out))
        for line in out[:60]:
            print(' ', line)
    else:
        print('① 聚合 JSON 与 golden 全等（数值 int/float 等价）OK')

    # 聚合 JSON → 渲染 → 与 target/report.pdf 逐页标题比对
    data_json = os.path.join(OUT_DIR, 'data.json')
    out_pdf = os.path.join(OUT_DIR, 'agg.pdf')
    with open(data_json, 'w', encoding='utf-8') as fp:
        json.dump(agg, fp, ensure_ascii=False)
    cp = ';'.join([os.path.join(RENDERER_CP, 'classes')] +
                  [os.path.join(RENDERER_CP, 'lib', j)
                   for j in os.listdir(os.path.join(RENDERER_CP, 'lib'))])
    java = os.path.join(os.environ.get('JAVA_HOME', ''), 'bin', 'java.exe')
    if not os.path.exists(java):
        java = 'java'
    r = subprocess.run([java, '-Xmx512m', '-cp', cp, 'com.aischool.render.RenderPdf',
                        data_json, out_pdf], capture_output=True, text=True, timeout=300,
                       cwd=RENDERER_CP)
    if not os.path.exists(out_pdf):
        print('渲染失败:', r.stdout[-500:], r.stderr[-500:])
        sys.exit(1)

    n, sig = title_sig(out_pdf)
    tn, tsig = title_sig(TARGET_PDF)
    if n != 50:
        print('② 渲染页数 %d != 50 FAIL' % n)
    elif sig != tsig:
        print('② 标题序列与 target/report.pdf 不一致 FAIL')
        for j, (a, b) in enumerate(zip(sig, tsig)):
            if a != b:
                print('  p%02d: %s vs %s' % (j + 1, a, b))
    else:
        print('② 渲染 %d 页、标题序列与 target/report.pdf 逐页一致 OK' % n)
    ok = not out and n == 50 and sig == tsig
    print('RESULT:', 'PASS' if ok else 'FAIL')
    sys.exit(0 if ok else 1)  # 退出码与 RESULT 一致（verify_m7 内嵌契约门依赖）


if __name__ == '__main__':
    main()
