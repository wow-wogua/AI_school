import fitz, re
S = fitz.open('D:/srp_project/AI_school/学生成长报告册.pdf')
R = fitz.open('D:/srp_project/AI_school/report-renderer/target/report.pdf')

def lines(doc, p):
    rows = {}
    d = doc[p].get_text('dict')
    for b in d['blocks']:
        for l in b.get('lines', []):
            for s in l['spans']:
                t = s['text'].strip()
                if not t: continue
                y = round(s['bbox'][1], 1)
                key = None
                for k in rows:
                    if abs(k - y) <= 1.6: key = k; break
                if key is None: key = y; rows[key] = []
                rows[key].append((s['bbox'][0], s['bbox'][2], t))
    out = []
    for y, sp in rows.items():
        sp.sort()
        txt = re.sub(r'\s+', '', ''.join(t for _, _, t in sp))
        out.append((y, round(sp[0][0], 1), round(sp[-1][1], 1), txt))
    return sorted(out)

TOL_Y, TOL_X = 1.2, 2.0
for p in range(0, 49):
    sl, rl = lines(S, p), lines(R, p)
    used = set(); bad = []; orphan_s = []
    for sy, sx0, sx1, st in sl:
        best = None
        for i, (ry, rx0, rx1, rt) in enumerate(rl):
            if i in used or rt != st: continue
            if abs(ry - sy) > 6 or abs(rx0 - sx0) > 8: continue
            best = i; break
        if best is None:
            orphan_s.append((sy, sx0, st[:18])); continue
        used.add(best)
        ry, rx0, rx1, rt = rl[best]
        if abs(ry - sy) > TOL_Y or abs(rx0 - sx0) > TOL_X:
            bad.append((st[:16], round(ry - sy, 2), round(rx0 - sx0, 2), sy))
    orphan_r = [(ry, rx0, rt[:16]) for i, (ry, rx0, rx1, rt) in enumerate(rl) if i not in used]
    status = 'OK' if not bad and not orphan_s and not orphan_r else 'DIFF'
    print(f'p{p+1:02d} {status}  bad={len(bad)} orphS={len(orphan_s)} orphR={len(orphan_r)} (S{len(sl)}/R{len(rl)})')
    for st, dy, dx, sy in bad[:14]:
        print(f'    Δ  "{st}" dy={dy:+.2f} dx={dx:+.2f} @{sy:.0f}')
    for sy, sx0, st in orphan_s[:8]:
        print(f'    S-orph "{st}" @({sx0:.0f},{sy:.0f})')
    for ry, rx0, rt in orphan_r[:8]:
        print(f'    R-orph "{rt}" @({rx0:.0f},{ry:.0f})')
