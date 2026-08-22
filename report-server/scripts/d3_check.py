# -*- coding: utf-8 -*-
"""D3 冒烟：容器栈经 nginx(80) 端到端验证。用法：python scripts/d3_check.py"""
import json, urllib.request

WEB = "http://localhost"

req = urllib.request.Request(WEB + "/api/auth/login", method="POST",
                             data=json.dumps({"username": "admin", "password": "admin123"}).encode(),
                             headers={"Content-Type": "application/json"})
tok = json.load(urllib.request.urlopen(req))["data"]["token"]

checks = []
def get(path, name, hook=None):
    req = urllib.request.Request(WEB + path)
    req.add_header("Authorization", "Bearer " + tok)
    d = json.load(urllib.request.urlopen(req))
    ok = d.get("code") == 0 and (hook(d["data"]) if hook else True)
    checks.append((name, "PASS" if ok else "FAIL: " + json.dumps(d, ensure_ascii=False)[:120]))

get("/api/admin/ai/usage?days=7", "容器内 AI 用量聚合")
get("/api/meta/terms", "学期列表", lambda d: len(d) > 0)

# 静态前端首页
html = urllib.request.urlopen(WEB + "/").read()
checks.append(("前端首页 HTML", "PASS" if b"app" in html[:2000] else "FAIL"))

for name, r in checks:
    print(f"{r}  {name}")
print("RESULT:", "PASS" if all(r == "PASS" for _, r in checks) else "FAIL")
