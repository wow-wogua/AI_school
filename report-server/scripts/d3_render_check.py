# -*- coding: utf-8 -*-
"""D3 冒烟：容器内单份报告渲染（渲染核心 + Chromium + 阿里云源装的中文字体）。
用法：python scripts/d3_render_check.py   → RESULT: PASS/FAIL"""
import json, time, urllib.request

WEB = "http://localhost"

def call(method, path, token=None, body=None):
    req = urllib.request.Request(WEB + path, method=method)
    if token: req.add_header("Authorization", "Bearer " + token)
    data = None
    if body is not None:
        req.add_header("Content-Type", "application/json")
        data = json.dumps(body).encode()
    with urllib.request.urlopen(req, data, timeout=120) as r:
        ct = r.headers.get("Content-Type", "")
        raw = r.read()
        if "json" in ct:
            return json.loads(raw)
        return raw

req = urllib.request.Request(WEB + "/api/auth/login", method="POST",
                             data=json.dumps({"username": "zhaolaoshi", "password": "aischool123"}).encode(),
                             headers={"Content-Type": "application/json"})
tok = json.load(urllib.request.urlopen(req))["data"]["token"]

students = call("GET", "/api/student/list?classId=2&page=1&size=3", tok)["data"]["records"]
sid = students[0]["id"]
terms = call("GET", "/api/meta/terms", tok)["data"]
tid = next((t["id"] for t in terms if t.get("isCurrent") == 1), terms[0]["id"])
print(f"student={students[0]['name']} term={tid}")

r = call("POST", "/api/report/generate", tok, {"studentId": sid, "termId": tid})
task_id = r["data"]["taskId"]
print("task:", task_id)
status = ""
for _ in range(60):
    time.sleep(3)
    t = call("GET", f"/api/report/task/{task_id}", tok)["data"]
    status = t.get("status", "")
    if status in ("成功", "失败"): break
print("status:", status)
if status != "成功":
    print("RESULT: FAIL"); raise SystemExit

rep = call("GET", f"/api/report/list?classId=2&termId={tid}", tok)["data"]
mine = next(r for r in rep if r.get("studentId") == sid)
rid = mine["reportId"]
pdf = call("GET", f"/api/report/file/{rid}", tok)
print("pdf bytes:", len(pdf), "magic:", pdf[:4])
ok = pdf[:4] == b"%PDF" and len(pdf) > 100_000
print("RESULT:", "PASS" if ok else "FAIL")
