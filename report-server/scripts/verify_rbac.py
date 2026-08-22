# -*- coding: utf-8 -*-
"""验收⑥ 权限验证：未登录 401 / 越权 403 / 角色隔离。
永久验证脚本：python scripts/verify_rbac.py [--base http://localhost:8080]
"""
import sys, json, time, urllib.request, urllib.error

BASE = "http://localhost:8080"
if "--base" in sys.argv:
    BASE = sys.argv[sys.argv.index("--base") + 1]

PASS, FAIL = 0, 0


def call(method, path, token=None, body=None, raw=False):
    req = urllib.request.Request(BASE + path, method=method)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = None
    if body is not None:
        req.add_header("Content-Type", "application/json")
        data = json.dumps(body).encode()
    try:
        with urllib.request.urlopen(req, data) as r:
            b = r.read()
            return r.status, b if raw else json.loads(b)
    except urllib.error.HTTPError as e:
        b = e.read()
        try:
            return e.code, json.loads(b)
        except Exception:
            return e.code, b


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
    assert st == 200 and r["code"] == 0, f"login {u} 失败: {st} {r}"
    return r["data"]["token"]


def main():
    admin = login("admin", "admin123")
    litao = login("litao", "aischool123")       # 班主任 初一1班
    zhao = login("zhaolaoshi", "aischool123")   # 班主任 初一2班
    wang = login("wanglaoshi", "aischool123")   # 任课教师 初一2班

    print("[1] 未登录 / 坏 token → 401")
    st, _ = call("GET", "/api/student/list?page=1&size=10")
    check("无 token student/list → 401", st == 401, f"got {st}")
    st, _ = call("GET", "/api/student/list?page=1&size=10", token="bad.token.here")
    check("坏 token → 401", st == 401, f"got {st}")
    st, _ = call("GET", "/api/report/data/1?termId=2")
    check("无 token report/data → 401", st == 401, f"got {st}")
    st, _ = call("GET", "/api/report/pdf/1?termId=2", raw=True)
    check("无 token pdf → 401", st == 401, f"got {st}")

    print("[2] 各角色可见班级隔离")
    st, r = call("GET", "/api/meta/my-classes", token=litao)
    names = [c["name"] for c in r["data"]]
    check("李老师(初一(1)班班主任)只见表1", names == ["初一(1)班"], f"got {names}")
    st, r = call("GET", "/api/meta/my-classes", token=wang)
    names = [c["name"] for c in r["data"]]
    check("王老师(任课初一(2)班)只见表2", names == ["初一(2)班"], f"got {names}")
    st, r = call("GET", "/api/meta/my-classes", token=admin)
    check("admin 见全部(2个)", len(r["data"]) == 2, f"got {len(r['data'])}")

    print("[3] 学生列表隔离 + 分页 total（MybatisPlusConfig 生效验证）")
    st, r = call("GET", "/api/student/list?classId=2&page=1&size=50", token=wang)
    check("王老师 class2 total=2", r["data"]["total"] == 2, f"got {r['data'].get('total')}")
    st, r = call("GET", "/api/student/list?classId=1&page=1&size=50", token=wang)
    check("王老师 class1 total=0（越权数据不可见）", r["data"]["total"] == 0, f"got {r['data'].get('total')}")
    st, r = call("GET", "/api/student/list?classId=1&page=1&size=5", token=litao)
    check("李老师 class1 total=50 分页正确", r["data"]["total"] == 50 and len(r["data"]["records"]) == 5,
          f"total={r['data'].get('total')} rows={len(r['data'].get('records', []))}")

    # 找 class2 的一个学生 id（供越权测试）
    st, r = call("GET", "/api/student/list?classId=2&page=1&size=10", token=zhao)
    c2_student = r["data"]["records"][0]["id"]

    print("[4] 越权 → 403")
    st, r = call("GET", f"/api/report/data/{c2_student}?termId=2", token=litao)
    check("李老师读 class2 学生数据 → 403", st == 403, f"got {st}")
    st, r = call("POST", "/api/report/generate", token=litao, body={"studentId": c2_student, "termId": 2})
    check("李老师给 class2 学生生成 → 403", st == 403, f"got {st}")
    st, r = call("POST", "/api/report/generate-batch", token=wang, body={"classId": 1, "termId": 2})
    check("王老师(任课)发起 class1 批量 → 403", st == 403, f"got {st}")
    st, r = call("POST", "/api/report/generate-batch", token=wang, body={"classId": 2, "termId": 2})
    check("王老师(任课)发起批量 → 403（仅管理员/本班班主任）", st == 403, f"got {st}")
    st, r = call("POST", "/api/ai/comment-draft", token=wang, body={"studentId": c2_student, "termId": 2})
    check("王老师(任课)生成寄语草稿 → 403", st == 403, f"got {st}")

    print("[5] 合法授权通过")
    st, r = call("POST", "/api/report/generate-batch", token=zhao, body={"classId": 2, "termId": 2})
    check("赵老师(初一2班班主任)发起本班批量 → 200", st == 200 and r["code"] == 0, f"got {st}")
    if st == 200:
        tid = r["data"]["taskId"]
        st, r = call("GET", f"/api/report/task/{tid}", token=litao)
        check("李老师查别人任务 → 403", st == 403, f"got {st}")
        # 等本任务渲染到终态再退出：批量是服务端异步渲染（JVM 持有 renderer/lib），
        # 不等完就跑 verify_retry 的 lib 换名会撞 WinError 5（链内确定性失败）
        t0 = time.time()
        while time.time() - t0 < 120:
            time.sleep(2)
            st, r = call("GET", f"/api/report/task/{tid}", token=zhao)
            if r["data"]["status"] in ("成功", "失败", "部分失败"):
                print(f"  [rbac] 批量任务 #{tid} 已到终态：{r['data']['status']}")
                break
    st, r = call("GET", f"/api/report/data/{c2_student}?termId=2", token=wang)
    check("王老师读本班学生数据 → 200", st == 200, f"got {st}")

    print(f"\nRESULT: {'PASS' if FAIL == 0 else 'FAIL'}  pass={PASS} fail={FAIL}")
    sys.exit(0 if FAIL == 0 else 1)


if __name__ == "__main__":
    main()
