# -*- coding: utf-8 -*-
"""M6 验收：§9 活动（CRUD+录参与+能量币）/ §10 荣誉（上传→确认）/ §8 时间轴 / p42 活动表渲染 / RBAC。
所有写入只走 class2 学生，学生 1（契约基线）零污染（首尾各断言一次）。
用法：PYTHONIOENCODING=utf-8 python scripts/verify_m6.py [--skip-render]
（--skip-render：模板 p42 改造前跳过第 5 组渲染断言）
"""
import base64
import io
import json
import sys
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080"
PASS, FAIL = 0, 0

# 1x1 像素有效 JPEG
JPEG_B64 = ("/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRof"
            "Hh0aHBwcJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPDIzNP/AABEIAAEAAQMBIgACEQEDEQH/"
            "xAAfAAABBQEBAQEBAQAAAAAAAAABAgMEBQYHCAkKC//EALUQAAIBAwMCBAMFBQQEAAABfQEC"
            "AwAEEQUSITFBBhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2Nzg5"
            "OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6g4SFhoeIiYqSk5SVlpeYmZqio6Sl"
            "pqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2drh4uPk5ebn6Onq8fLz9PX29/j5+v/aA"
            "AwDAQACEQMRAD8A/v4ooooA/9k=")


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


def call_raw(method, path, token=None, body=None):
    """返回 (status, bytes, content_type)；4xx/5xx 不抛异常"""
    req = urllib.request.Request(BASE + path, method=method)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = None
    if body is not None:
        req.add_header("Content-Type", "application/json")
        data = json.dumps(body).encode()
    try:
        with urllib.request.urlopen(req, data) as r:
            return r.status, r.read(), r.headers.get("Content-Type", "")
    except urllib.error.HTTPError as e:
        return e.code, e.read(), e.headers.get("Content-Type", "")


def login(username, password):
    return call("POST", "/api/auth/login", body={"username": username, "password": password})["data"]["token"]


def check(name, cond, detail=""):
    global PASS, FAIL
    print(f"  {'PASS' if cond else 'FAIL'} {name}  {detail}")
    if cond:
        PASS += 1
    else:
        FAIL += 1


def multipart_upload(token, path, fields, file_field, filename, content, ctype):
    boundary = "----aischoolboundary7d1a2c"
    parts = []
    for k, v in fields.items():
        parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n".encode())
    parts.append((f"--{boundary}\r\nContent-Disposition: form-data; name=\"{file_field}\"; "
                  f"filename=\"{filename}\"\r\nContent-Type: {ctype}\r\n\r\n").encode())
    parts.append(content)
    parts.append(f"\r\n--{boundary}--\r\n".encode())
    body = b"".join(parts)
    req = urllib.request.Request(BASE + path, method="POST", data=body)
    req.add_header("Authorization", "Bearer " + token)
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())


def wait_task(task_id, token, timeout=120):
    t0 = time.time()
    while time.time() - t0 < timeout:
        time.sleep(2)
        p = call("GET", f"/api/report/task/{task_id}", token)["data"]
        if p["status"] in ("成功", "失败", "部分失败"):
            return p, time.time() - t0
    return p, timeout


def main():
    skip_render = "--skip-render" in sys.argv
    admin = login("admin", "admin123")

    # 学生 1 契约基线（首）
    base1 = call("GET", "/api/report/data/1?termId=2", admin)["data"]

    # class2 学生动态获取
    students = call("GET", "/api/student/list?classId=2&page=1&size=10", admin)["data"]
    sid = students["records"][0]["id"]
    sname = students["records"][0]["name"]
    print(f"== M6 ==  测试学生：{sname}(id={sid}, class2)")

    before = call("GET", f"/api/report/data/{sid}?termId=2", admin)["data"]
    total_before = before["coin"]["total"]
    top5_before = [x["name"] for x in before["coin"]["incomeTop5"]]

    print("== ① 活动 CRUD ==")
    act = call("POST", "/api/activity", admin, {
        "title": "M6验收-校园演讲比赛", "type": "比赛",
        "startTime": "2026-03-10T09:00:00", "place": "报告厅", "intro": "验收用"}).get("data") or {}
    aid = act.get("activityId")
    check("admin 建活动", aid is not None, f"activityId={aid}")
    call("PUT", f"/api/activity/{aid}", admin, {
        "title": "M6验收-校园演讲比赛", "type": "文体",
        "startTime": "2026-03-10T09:00:00", "place": "大礼堂", "intro": "验收用(改)"})
    lst = call("GET", "/api/activity/list", admin)["data"]
    row = next((x for x in lst if x["id"] == aid), None)
    check("PUT 改活动生效", row is not None and row["place"] == "大礼堂" and row["type"] == "文体",
          f"place={row and row['place']}")

    print("== ② 录参与 + 能量币入账 ==")
    r = call("POST", f"/api/activity/{aid}/signup", admin, {
        "studentId": sid, "checkin": True, "award": "一等奖",
        "performance": "台风稳健", "coin": 20}).get("data") or {}
    check("录获奖返回 termId=2", r.get("termId") == 2, f"termId={r.get('termId')} signupId={r.get('signupId')}")
    after = call("GET", f"/api/report/data/{sid}?termId=2", admin)["data"]
    acts = after["activities"]
    check("聚合 activities 出现该活动", any(a.get("title") == "M6验收-校园演讲比赛"
          and a.get("award") == "一等奖" for a in acts), f"n={len(acts)} {json.dumps(acts, ensure_ascii=False)[:120]}")
    check("coin.total +20", abs(after["coin"]["total"] - total_before - 20) < 1e-6,
          f"{total_before} -> {after['coin']['total']}")
    # 收入榜按模块金额排序，活动/荣誉金额大时不进 least3（只体现在 total/current）；
    # 关键契约属性是新入账不打乱既有评价模块的 TOP5 榜序
    top5_after = [x["name"] for x in after["coin"]["incomeTop5"]]
    check("收入TOP5榜序不受新模块影响", top5_after == top5_before, f"{top5_after}")
    # 无奖项附币 → 400
    st, body, _ = call_raw("POST", f"/api/activity/{aid}/signup", admin,
                           {"studentId": sid, "coin": 5})
    check("无奖项附币被拒(400)", st == 400, f"status={st}")
    # 带参与记录的活动不可删
    st, body, _ = call_raw("DELETE", f"/api/activity/{aid}", admin)
    check("带参与记录 DELETE 被拒(400)", st == 400, f"status={st}")

    print("== ③ 荣誉：上传→确认 ==")
    up = multipart_upload(admin, "/api/honor/upload", {"studentId": str(sid)},
                          "file", "cert.jpg", base64.b64decode(JPEG_B64), "image/jpeg")["data"]
    check("上传落库 待确认", up.get("honorId") is not None, f"source={up.get('source')} detail={up.get('detail')}")
    check("AI 未配置 → source=manual", up.get("source") == "manual", "")
    hid = up["honorId"]
    call("PUT", f"/api/honor/{hid}", admin, {"name": "区级书法大赛一等奖", "level": "区级",
                                             "issuer": "区教育局", "honorDate": "2026-03-15"})
    cf = call("PUT", f"/api/honor/{hid}/confirm", admin, {"coin": 30}).get("data") or {}
    check("确认入币 termId=2", cf.get("termId") == 2, f"termId={cf.get('termId')}")
    after2 = call("GET", f"/api/report/data/{sid}?termId=2", admin)["data"]
    check("coin.total 再+30", abs(after2["coin"]["total"] - after["coin"]["total"] - 30) < 1e-6,
          f"{after['coin']['total']} -> {after2['coin']['total']}")
    hlst = call("GET", f"/api/honor/list?studentId={sid}", admin)["data"]
    hrow = next((h for h in hlst if h["id"] == hid), None)
    check("列表已确认", hrow is not None and hrow["confirmStatus"] == "已确认" and hrow["name"] == "区级书法大赛一等奖",
          f"status={hrow and hrow['confirmStatus']}")
    st, body, ctype = call_raw("GET", f"/api/honor/file/{hid}", admin)
    check("证书原件可取(200 image/jpeg)", st == 200 and "image/jpeg" in ctype, f"{st} {ctype} {len(body)}B")
    # 重复确认 → 400
    st, body, _ = call_raw("PUT", f"/api/honor/{hid}/confirm", admin, {"coin": 0})
    check("重复确认被拒(400)", st == 400, f"status={st}")
    # 待确认可删（新建一个不确认直接删）
    up2 = multipart_upload(admin, "/api/honor/upload", {"studentId": str(sid)},
                           "file", "tmp.jpg", base64.b64decode(JPEG_B64), "image/jpeg")["data"]
    st, _, _ = call_raw("DELETE", f"/api/honor/{up2['honorId']}", admin)
    check("待确认荣誉可删", st == 200, f"status={st}")

    print("== ④ 时间轴 ==")
    ev = call("GET", f"/api/timeline/{sid}?termId=2", admin)["data"]["events"]
    types = {e["type"] for e in ev}
    check("事件含 评价/活动/荣誉", {"评价", "活动", "荣誉"} <= types, f"types={sorted(types)} n={len(ev)}")
    check("活动事件含『荣获』", any(e["type"] == "活动" and "荣获一等奖" in e["detail"] for e in ev), "")
    times = [e["time"] for e in ev if e["type"] != "成绩"]  # 成绩为纯日期，仅对同类比较
    check("time 倒序", times == sorted(times, reverse=True), f"head={times[:3]}")
    ev1 = call("GET", "/api/timeline/1?termId=2", admin)["data"]["events"]
    check("学生1 无活动/荣誉事件", all(e["type"] not in ("活动", "荣誉") for e in ev1),
          f"types={sorted({e['type'] for e in ev1})}")

    print("== ⑤ 渲染 p42 活动表 ==")
    if skip_render:
        print("  SKIP（--skip-render：模板改造前）")
    else:
        t = call("POST", "/api/report/generate", admin, {"studentId": sid, "termId": 2})["data"]
        p, secs = wait_task(t["taskId"], admin)
        check("单份生成成功", p["status"] == "成功", f"{p['status']} in {secs:.0f}s")
        lst2 = call("GET", f"/api/report/list?classId=2&termId=2", admin)["data"]
        rid = next((x["reportId"] for x in lst2 if x["studentId"] == sid and x["status"] == "成功"), None)
        check("报告记录存在", rid is not None, f"reportId={rid}")
        if rid:
            st, pdf, ctype = call_raw("GET", f"/api/report/file/{rid}", admin)
            import fitz
            doc = fitz.open(stream=pdf, filetype="pdf")
            check("PDF 仍 51 页", doc.page_count == 51, f"pages={doc.page_count}")  # 50 内容页+空白尾页（换校批次起存在，契约/基线同口径）
            idx = next((i for i in range(doc.page_count)
                        if "主题活动参与记录" in doc[i].get_text()), None)
            text = doc[idx].get_text() if idx is not None else ""
            check("活动页含活动名+奖项+空态消失", idx is not None
                  and "M6验收-校园演讲比赛" in text and "一等奖" in text
                  and "不要气馁" not in text, f"pageIndex0={idx}")
            doc.close()

    print("== ⑥ RBAC ==")
    litao = login("litao", "aischool123")
    zhao = login("zhaolaoshi", "aischool123")
    wang = login("wanglaoshi", "aischool123")
    st, _, _ = call_raw("POST", "/api/activity", litao, {"title": "x", "startTime": "2026-03-10T09:00:00"})
    check("班主任建活动→403", st == 403, f"status={st}")
    st, _, _ = call_raw("POST", f"/api/activity/{aid}/signup", litao,
                        {"studentId": sid, "award": "x"})
    check("跨班班主任录参与→403", st == 403, f"status={st}")
    st, _, _ = call_raw("POST", f"/api/activity/{aid}/signup", zhao,
                        {"studentId": sid, "performance": "本班班主任补录"})
    check("本班班主任录参与→200", st == 200, f"status={st}")
    st, _, _ = call_raw("POST", f"/api/activity/{aid}/signup", wang,
                        {"studentId": sid, "performance": "x"})
    check("任课教师录参与→403", st == 403, f"status={st}")
    st, _, _ = call_raw("GET", "/api/activity/list")
    check("未登录→401", st == 401, f"status={st}")

    print("== ⑦ 学生1 零污染 ==")
    end1 = call("GET", "/api/report/data/1?termId=2", admin)["data"]
    check("学生1 activities==[]", end1["activities"] == [], f"{end1['activities']}")
    check("学生1 coin.total 不变", end1["coin"]["total"] == base1["coin"]["total"],
          f"{base1['coin']['total']} -> {end1['coin']['total']}")

    print(f"\nRESULT: {'PASS' if FAIL == 0 else 'FAIL'}  pass={PASS} fail={FAIL}")
    sys.exit(0 if FAIL == 0 else 1)


if __name__ == "__main__":
    main()
