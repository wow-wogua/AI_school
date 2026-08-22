# -*- coding: utf-8 -*-
"""D3 冒烟：容器产 PDF 首页栅格化存图（人工/视觉核验中文字体）。用法：python scripts/d3_pdf_page1.py"""
import json, urllib.request, pathlib, sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import fitz

WEB = "http://localhost"
req = urllib.request.Request(WEB + "/api/auth/login", method="POST",
                             data=json.dumps({"username": "zhaolaoshi", "password": "aischool123"}).encode(),
                             headers={"Content-Type": "application/json"})
tok = json.load(urllib.request.urlopen(req))["data"]["token"]

req = urllib.request.Request(WEB + "/api/report/list?classId=2&termId=2")
req.add_header("Authorization", "Bearer " + tok)
rep = json.load(urllib.request.urlopen(req))["data"]
rid = next(r for r in rep if r.get("reportId"))["reportId"]  # 任取一份已成功的报告

req = urllib.request.Request(WEB + f"/api/report/file/{rid}")
req.add_header("Authorization", "Bearer " + tok)
pdf = urllib.request.urlopen(req).read()

doc = fitz.open(stream=pdf, filetype="pdf")
out = pathlib.Path(__file__).parents[2] / "report-web" / "e2e" / "shots" / "d3_container_pdf_p1.png"
pix = doc[0].get_pixmap(dpi=100)
pix.save(out)
text = doc[0].get_text()[:80].replace("\n", " ")
print("page1 text head:", text)
print("saved:", out)
