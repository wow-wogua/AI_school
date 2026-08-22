# -*- coding: utf-8 -*-
"""验收③ 失败可重试：临时移走渲染 classpath → class2 批量应失败 → 还原 → retry → 应成功。
脚本自带还原（finally），可反复运行。python scripts/verify_retry.py
"""
import sys, json, time, subprocess, shutil, os, pathlib, urllib.request

BASE = "http://localhost:8080"
RENDERER = pathlib.Path(__file__).resolve().parents[2] / "report-renderer" / "target"
PASS, FAIL = 0, 0


def call(method, path, token=None, body=None):
    req = urllib.request.Request(BASE + path, method=method)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = None
    if body is not None:
        req.add_header("Content-Type", "application/json")
        data = json.dumps(body).encode()
    with urllib.request.urlopen(req, data) as r:
        return json.loads(r.read())


def check(name, cond, detail=""):
    global PASS, FAIL
    print(f"  {'PASS' if cond else 'FAIL'} {name}  {detail}")
    if cond:
        PASS += 1
    else:
        FAIL += 1


def wait_terminal(tid, tok, timeout=180):
    t0 = time.time()
    while time.time() - t0 < timeout:
        time.sleep(2)
        p = call("GET", f"/api/report/task/{tid}", token=tok)["data"]
        if p["status"] in ("成功", "失败", "部分失败"):
            return p, time.time() - t0
    return p, timeout


def swap_lib(src: pathlib.Path, dst: pathlib.Path):
    """目录换名用 os.rename + 重试：渲染 JVM 退出瞬间/杀软扫描可能短暂持有 lib 内 jar
    句柄，rename 失败重试即可。不用 shutil.move——它 rename 失败后走 copytree+rmtree
    兜底，rmtree 撞锁会把 src 删成半截（WinError 32 的根因）。"""
    for i in range(6):
        try:
            if dst.exists():
                shutil.rmtree(dst)          # 上次异常退出的残留
            os.rename(src, dst)
            return
        except PermissionError:
            if i == 5:
                raise
            time.sleep(1.0)


def main():
    r = call("POST", "/api/auth/login", body={"username": "admin", "password": "admin123"})
    tok = r["data"]["token"]
    lib, bak = RENDERER / "lib", RENDERER / "lib.bak"
    assert lib.exists(), f"渲染 classpath 不存在: {lib}"
    try:
        swap_lib(lib, bak)
        print("已临时移走渲染 classpath（制造失败）")
        t = call("POST", "/api/report/generate-batch", token=tok, body={"classId": 2, "termId": 2})["data"]
        p, secs = wait_terminal(t["taskId"], tok)
        check("批量全部失败（渲染不可用）", p["status"] == "失败" and p["failed"] > 0,
              f"{p['status']} failed={p['failed']} in {secs:.0f}s")
    finally:
        if bak.exists() and not lib.exists():
            swap_lib(bak, lib)
            print("已还原渲染 classpath")

    r = call("POST", f"/api/report/task/{t['taskId']}/retry", token=tok)["data"]
    check("retry 接受重试", r["status"] == "排队", r["status"])
    p, secs = wait_terminal(t["taskId"], tok)
    check("重试后全部成功", p["status"] == "成功" and p["failed"] == 0,
          f"{p['status']} done={p['done']} failed={p['failed']} in {secs:.0f}s")

    lst = call("GET", "/api/report/list?classId=2&termId=2", token=tok)["data"]
    rid = next(x["reportId"] for x in lst if x["status"] == "成功")
    req = urllib.request.Request(f"{BASE}/api/report/file/{rid}", headers={"Authorization": "Bearer " + tok})
    with urllib.request.urlopen(req) as resp:
        b = resp.read()
    check("重试产物 PDF 可下载", resp.status == 200 and b[:5] == b"%PDF-", f"{len(b)}B")

    print(f"\nRESULT: {'PASS' if FAIL == 0 else 'FAIL'}  pass={PASS} fail={FAIL}")
    sys.exit(0 if FAIL == 0 else 1)


if __name__ == "__main__":
    main()
