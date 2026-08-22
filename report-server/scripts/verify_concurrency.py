# -*- coding: utf-8 -*-
"""验收③④ 并发验证：
  ③ 50 人班级批量 ≤5 分钟、进度实时单调递增、0 失败
  ④ 批量进行中（渲染池满载 ≥4 并发）：
     - 单份生成仍 ≤30s（优先级插队，互不干扰）
     - Web API 响应无劣化（<2s）
     - 已生成 PDF 可正常预览/下载
永久脚本：python scripts/verify_concurrency.py
"""
import sys, json, time, urllib.request, urllib.error

BASE = "http://localhost:8080"
PASS, FAIL = 0, 0


def call(method, path, token=None, body=None, raw=False):
    req = urllib.request.Request(BASE + path, method=method)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = None
    if body is not None:
        req.add_header("Content-Type", "application/json")
        data = json.dumps(body).encode()
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, data) as r:
            b = r.read()
            return r.status, (b if raw else json.loads(b)), time.time() - t0
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read()), time.time() - t0
        except Exception:
            return e.code, {}, time.time() - t0


def check(name, cond, detail=""):
    global PASS, FAIL
    tag = "PASS" if cond else "FAIL"
    if cond:
        PASS += 1
    else:
        FAIL += 1
    print(f"  [{time.strftime('%H:%M:%S')}] {tag} {name}  {detail}")


def main():
    _, r, _ = call("POST", "/api/auth/login", body={"username": "admin", "password": "admin123"})
    tok = r["data"]["token"]

    print("== 验收③：50 人班级批量（≤5 分钟、进度实时、0 失败）==")
    _, r, _ = call("POST", "/api/report/generate-batch", token=tok, body={"classId": 1, "termId": 2})
    tid = r["data"]["taskId"]
    print(f"批量任务 #{tid}（初一(1)班 50 人）已发起")

    t0 = time.time()
    done_hist, api_lats = [], []
    single_done, single_secs, mid_pdf_ok = None, None, None

    while True:
        time.sleep(5)
        _, p, lat = call("GET", f"/api/report/task/{tid}", token=tok)
        p = p.get("data", p)
        done_hist.append(p["done"])
        api_lats.append(lat)
        print(f"  [{int(time.time()-t0):3d}s] {p['status']} done={p['done']}/{p['total']} failed={p['failed']}")

        # ④ 批量满载 20s 后：单份插队生成（class2 学生，不占批量队列）
        if single_done is None and time.time() - t0 > 20:
            s0 = time.time()
            _, r1, _ = call("POST", "/api/report/generate", token=tok, body={"studentId": 51, "termId": 2})
            stid = r1["data"]["taskId"]
            print(f"  → 批量进行中发起单份生成（任务#{stid}，优先级插队）")
            while time.time() - s0 < 60:
                time.sleep(2)
                _, sp, _ = call("GET", f"/api/report/task/{stid}", token=tok)
                sp = sp.get("data", sp)
                if sp["status"] in ("成功", "失败", "部分失败"):
                    single_secs = time.time() - s0
                    single_done = sp["status"]
                    check("④ 批量中单份生成 ≤30s", single_done == "成功" and single_secs <= 30,
                          f"{single_done} in {single_secs:.0f}s")
                    break
            if single_done is None:
                single_done, single_secs = "超时", 60
                check("④ 批量中单份生成 ≤30s", False, "60s 未完成")

        # ④ 批量中：已完成的报告可下载（预览通路）
        if mid_pdf_ok is None and p["done"] >= 5:
            _, lst, _ = call("GET", "/api/report/list?classId=1&termId=2", token=tok)
            rid = next((x["reportId"] for x in lst["data"] if x["status"] == "成功"), None)
            if rid:
                st, b, dlat = call("GET", f"/api/report/file/{rid}", token=tok, raw=True)
                ok = st == 200 and bytes(b[:5]) == b"%PDF-" and len(b) > 100000
                mid_pdf_ok = ok
                check("④ 批量中 PDF 下载完整", ok, f"reportId={rid} {len(b)}B {dlat:.2f}s")

        if p["status"] in ("成功", "失败", "部分失败"):
            total_secs = time.time() - t0
            break
        if time.time() - t0 > 330:
            total_secs = time.time() - t0
            break

    check("③ 50 人批量 ≤300s", p["status"] == "成功" and total_secs <= 300,
          f"{p['status']} in {total_secs:.0f}s")
    check("③ 批量 0 失败", p["failed"] == 0, f"failed={p['failed']}")
    check("③ 进度单调不减", all(a <= b for a, b in zip(done_hist, done_hist[1:])), str(done_hist))
    check("④ 批量中任务查询 API <2s", max(api_lats) < 2, f"max={max(api_lats):.2f}s")

    # ④ 批量后单份再验一次 30s（无批量干扰的基线）
    s0 = time.time()
    _, r2, _ = call("POST", "/api/report/generate", token=tok, body={"studentId": 52, "termId": 2})
    sid = r2["data"]["taskId"]
    while time.time() - s0 < 60:
        time.sleep(2)
        _, sp, _ = call("GET", f"/api/report/task/{sid}", token=tok)
        sp = sp.get("data", sp)
        if sp["status"] in ("成功", "失败", "部分失败"):
            check("② 空闲时单份生成 ≤30s", sp["status"] == "成功" and time.time() - s0 <= 30,
                  f"{sp['status']} in {time.time()-s0:.0f}s")
            break

    print(f"\nRESULT: {'PASS' if FAIL == 0 else 'FAIL'}  pass={PASS} fail={FAIL}")
    sys.exit(0 if FAIL == 0 else 1)


if __name__ == "__main__":
    main()
