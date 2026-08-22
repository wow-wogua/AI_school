# -*- coding: utf-8 -*-
"""提示词对比评测：固定学生集提交 寄语+总结 任务 → 轮询完成 → 输出 markdown 对照表。
改提示词前后各跑一次本脚本（改 application.yml 的 aischool.ai.prompts.* 或对应环境变量后重启后端），
diff 两份输出人工比对效果差异。

前置：后端 8080 已起、AI 已配 key（未配 key 会降级模板，输出无对比意义）。
用法：
  python scripts/prompt_eval.py            # 初一(2)班全部学生（zhaolaoshi 可操作），当前学期
  python scripts/prompt_eval.py 53,54      # 指定学生 id
输出：scripts/prompt_eval_out/评测_YYYYmmdd_HHMMSS.md
"""
import sys, os, json, time, urllib.request, urllib.error
from datetime import datetime

BASE = "http://localhost:8080"
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "prompt_eval_out")
POLL_INTERVAL, TIMEOUT = 3, 300


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


def login(u, p):
    st, r = call("POST", "/api/auth/login", body={"username": u, "password": p})
    if st != 200:
        sys.exit(f"登录失败: {u} ({st})")
    return r["data"]["token"]


def wait_task(token, task_id):
    """轮询到终态，返回任务详情 dict"""
    deadline = time.time() + TIMEOUT
    while time.time() < deadline:
        st, r = call("GET", f"/api/ai/tasks/{task_id}", token=token)
        t = r.get("data", {})
        if t.get("status") in ("成功", "失败"):
            return t
        time.sleep(POLL_INTERVAL)
    return {"status": "超时"}


def main():
    token = login("zhaolaoshi", "aischool123")  # 初一(2)班班主任

    # 学生集：命令行指定 或 初一(2)班全部在读
    st, r = call("GET", "/api/student/list?classId=2&page=1&size=100", token=token)
    students = r["data"]["records"]
    if len(sys.argv) > 1:
        want = {int(x) for x in sys.argv[1].split(",")}
        students = [s for s in students if s["id"] in want]
    if not students:
        sys.exit("学生集为空（检查参数或班级数据）")

    st, r = call("GET", "/api/meta/terms", token=token)
    term_id = next((t["id"] for t in r["data"] if t.get("isCurrent") == 1), r["data"][0]["id"])
    print(f"学生 {len(students)} 人，termId={term_id}，提交 寄语+总结 各一份…")

    tasks = []  # (学生名, 类型, taskId)
    for s in students:
        for tp in ("COMMENT", "SUMMARY"):
            st, r = call("POST", "/api/ai/tasks", token=token,
                         body={"type": tp, "studentId": s["id"], "termId": term_id})
            if st != 200:
                sys.exit(f"提交失败: {s['name']} {tp} → {st} {r}")
            tasks.append((s["name"], tp, r["data"]["taskId"]))

    print(f"已入队 {len(tasks)} 个任务，轮询等待（每 {POLL_INTERVAL}s）…")
    done = {}
    for name, tp, tid in tasks:
        done[(name, tp)] = wait_task(token, tid)
        print(f"  {name} {tp}: {done[(name, tp)]['status']}")

    # 输出 markdown
    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, "评测_" + datetime.now().strftime("%Y%m%d_%H%M%S") + ".md")
    ok = 0
    with open(out, "w", encoding="utf-8") as f:
        f.write(f"# 提示词评测 {datetime.now():%Y-%m-%d %H:%M}\n\n")
        f.write(f"学生 {len(students)} 人 × 寄语+总结，termId={term_id}。\n")
        f.write("对比方法：改提示词前后各跑一次，diff 两份本文件。\n\n")
        for name, tp, tid in tasks:
            t = done[(name, tp)]
            label = "寄语" if tp == "COMMENT" else "总结"
            res = t.get("result") or {}
            src = res.get("source", t.get("source", ""))
            tok = ""
            if res.get("promptTokens") is not None:
                tok = f"（tokens {res['promptTokens']}/{res.get('completionTokens', 0)}）"
            f.write(f"## {name} · {label} [{src}{tok}]\n\n")
            text = res.get("draft") if tp == "COMMENT" else res.get("raw")
            if not text and res.get("blocks"):
                text = "\n".join(f"{k}：{v}" for k, v in res["blocks"].items())
            f.write(f"{text or t.get('error') or '（无输出）'}\n\n")
            if t.get("status") == "成功":
                ok += 1
    print(f"\n完成 {ok}/{len(tasks)}，输出：{out}")
    print("RESULT:", "PASS" if ok == len(tasks) else "PARTIAL")


if __name__ == "__main__":
    main()
