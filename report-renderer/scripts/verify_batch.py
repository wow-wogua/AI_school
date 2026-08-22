# Phase E 校验：10 份变体 PDF 全部 50 页，且每页中文标题序列与 golden 渲染一致（分页一致）
import fitz, re
BASE = 'D:/srp_project/AI_school/report-renderer/target/report.pdf'
def sig(doc):
    pages = []
    for pg in doc:
        titles = []
        for b in pg.get_text('dict')['blocks']:
            for l in b.get('lines', []):
                for s in l['spans']:
                    if s['size'] >= 12.9 and re.search(r'[\u4e00-\u9fff]', s['text']):
                        titles.append(s['text'])
        pages.append(tuple(titles))
    return pages
base = sig(fitz.open(BASE))
print('golden 页数:', len(base))
ok = True
for i in range(1, 11):
    p = f'D:/srp_project/AI_school/report-renderer/target/batch/student{i:02d}.pdf'
    d = fitz.open(p)
    s = sig(d)
    if len(d) != 50:
        print(f'student{i:02d}: 页数 {len(d)} != 50  FAIL'); ok = False; continue
    if s != base:
        for j, (a, b) in enumerate(zip(s, base)):
            if a != b:
                print(f'student{i:02d}: p{j+1} 标题不同: {a} vs {b}'); ok = False
        if len(s) != len(base):
            print(f'student{i:02d}: 签名页数 {len(s)} vs {len(base)}'); ok = False
    else:
        print(f'student{i:02d}: 50 页, 分页一致 OK')
print('RESULT:', 'PASS' if ok else 'FAIL')
