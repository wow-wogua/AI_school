# -*- coding: utf-8 -*-
"""AI 服务层冒烟：分析(规则引擎) → 寄语草稿(模板/LLM) → 编辑保存 → 确认生效 → 成长总结。
硬数字走规则引擎、AI 只产草稿的人工闭环验证。永久脚本：python scripts/verify_ai.py
"""
import sys, json, urllib.request, urllib.error

BASE = "http://localhost:8080"
PASS, FAIL = 0, 0


def call(method, path, token=None, body=None):
    req = urllib.request.Request(BASE + path, method=method)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = None
    if body is not None:
        req.add_header("Content-Type", "application/json")
        data = json.dumps(body).encode()
    try:
        with urllib.request.urlopen(req, data) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read())
        except Exception:
            return e.code, {}


def check(name, cond, detail=""):
    global PASS, FAIL
    if cond:
        PASS += 1
        print(f"  PASS {name}")
    else:
        FAIL += 1
        print(f"  FAIL {name}  {detail}")


def login(u, p):
    st, r = call("POST", "/api/auth/login", body={"username": u, "password": p})
    return r["data"]["token"]


def main():
    litao = login("litao", "aischool123")  # 初一(1)班班主任
    student, term = 2, 2  # 陈晓明? student 2 属于 class1

    print("[0] 前置：确认 student 2 属于初一(1)班（litao 可操作）")
    st, r = call("GET", "/api/student/list?classId=1&page=1&size=5", token=litao)
    ids = [s["id"] for s in r["data"]["records"]]
    check("student 2 在 class1", 2 in ids, f"ids={ids}")

    print("[1] 学业分析（规则引擎硬数字，不经 LLM）")
    st, r = call("POST", "/api/ai/analysis", token=litao, body={"studentId": student, "termId": term})
    check("analysis → 200", st == 200 and r["code"] == 0, f"got {st}")
    a = r.get("data", {})
    check("analysis 含 advantage/toImprove/trend", all(k in a for k in ("advantage", "toImprove", "trend")),
          f"keys={list(a.keys())}")
    print(f"    优势={a.get('advantage')}")
    print(f"    欠缺={a.get('toImprove')}")

    print("[2] 班主任寄语 AI 草稿（未配 LLM 时走模板）")
    st, r = call("POST", "/api/ai/comment-draft", token=litao, body={"studentId": student, "termId": term})
    check("comment-draft → 200", st == 200 and r["code"] == 0, f"got {st}")
    d = r.get("data", {})
    check("草稿非空且有来源标记", bool(d.get("draft")) and d.get("source") in ("template", "llm"),
          f"source={d.get('source')} len={len(d.get('draft', ''))}")
    print(f"    source={d.get('source')}  草稿: {d.get('draft', '')[:60]}…")
    draft = d.get("draft", "")

    print("[3] 读取草稿（GET comment：草稿存 aiDraft，已确认 content 不被覆盖）")
    st, r = call("GET", f"/api/ai/comment?studentId={student}&termId={term}", token=litao)
    c = r.get("data", {})
    check("GET comment 返回 aiDraft 草稿", st == 200 and c.get("aiDraft") == draft,
          f"aiDraft={str(c.get('aiDraft'))[:40]}…")
    check("已确认内容不被草稿覆盖", c.get("status") != "AI草稿" or not c.get("content"),
          f"status={c.get('status')} content={str(c.get('content'))[:30]}…")

    print("[4] 人工编辑保存 → 确认生效")
    edited = draft + "\n（教师人工补充：望新学期再接再厉。）"
    st, r = call("PUT", "/api/ai/comment", token=litao,
                 body={"studentId": student, "termId": term, "content": edited, "confirm": True})
    check("PUT confirm=true → 已确认", st == 200 and r["data"].get("status") == "已确认",
          f"got {r.get('data', {}).get('status')}")

    print("[5] 成长总结四块草稿")
    st, r = call("POST", "/api/ai/summary", token=litao, body={"studentId": student, "termId": term})
    check("summary → 200", st == 200 and r["code"] == 0, f"got {st}")
    s = r.get("data", {})
    blocks = s.get("blocks", {})
    check("总结含四个板块", len(blocks) == 4 and all(bool(v) for v in blocks.values()),
          f"blocks={list(blocks.keys())}")

    print("[6] 报告数据仍零漂移（寄语已确认进入聚合 JSON）")
    st, r = call("GET", f"/api/report/data/{student}?termId={term}", token=litao)
    hc = r["data"].get("headTeacherComment")
    check("聚合 JSON 的寄语 = 已确认内容", hc == edited, f"got {str(hc)[:50]}…")

    print(f"\nRESULT: {'PASS' if FAIL == 0 else 'FAIL'}  pass={PASS} fail={FAIL}")
    sys.exit(0 if FAIL == 0 else 1)


if __name__ == "__main__":
    main()
