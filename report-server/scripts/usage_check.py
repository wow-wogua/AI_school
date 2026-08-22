# -*- coding: utf-8 -*-
"""D2 冒烟：管理端 AI 用量聚合是否反映刚跑的真实任务。用法：python scripts/usage_check.py"""
import json, urllib.request

BASE = "http://localhost:8080"

req = urllib.request.Request(BASE + "/api/auth/login", method="POST",
                             data=json.dumps({"username": "admin", "password": "admin123"}).encode(),
                             headers={"Content-Type": "application/json"})
tok = json.load(urllib.request.urlopen(req))["data"]["token"]

req = urllib.request.Request(BASE + "/api/admin/ai/usage?days=30")
req.add_header("Authorization", "Bearer " + tok)
d = json.load(urllib.request.urlopen(req))["data"]
print("byDay:", json.dumps(d["byDay"], ensure_ascii=False))
print("byTeacher:", json.dumps(d["byTeacher"], ensure_ascii=False))
ok = any(r["promptTokens"] > 0 for r in d["byDay"]) and any(r["teacher"] for r in d["byTeacher"])
print("AGG:", "PASS" if ok else "FAIL")
