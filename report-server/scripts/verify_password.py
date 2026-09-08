# -*- coding: utf-8 -*-
"""密码策略验收：首次登录强制改密 + 管理员设密口径统一（≥8，NIST SP 800-63B / OWASP 对齐）。

覆盖：
  ① 管理员建号/重置密码：<8 位拒绝；成功后登录响应 mustChangePassword=true
  ② 强制改密拦截：带 mcp token 访问业务接口 → 403（仅放行 /api/auth/password|me|login）
  ③ 改密闭环：旧密码校验、<8 位拒绝、成功返回新 token（mcp 已清），业务恢复，重登 mustChangePassword=false
  ④ 批量导入：初始密码 Shishi@2026 登录 → mustChangePassword=true
所有写入用 qa_pw_* 临时账号，末尾清理，不动种子数据。
用法：PYTHONIOENCODING=utf-8 python scripts/verify_password.py
"""
import io
import json
import sys
import time
import urllib.error
import urllib.request
import zipfile

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
    with urllib.request.urlopen(req, data) as r:
        return json.loads(r.read())


def call_raw(method, path, token=None, body=None):
    """返回 (status, body_dict)；4xx/5xx 不抛异常"""
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
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw[:120].decode("utf-8", "ignore")}


def login(username, password):
    return call("POST", "/api/auth/login", body={"username": username, "password": password})["data"]


def check(name, cond, detail=""):
    global PASS, FAIL
    print(f"  {'PASS' if cond else 'FAIL'} {name}  {detail}")
    if cond:
        PASS += 1
    else:
        FAIL += 1


def build_teacher_xlsx(row):
    """最小 xlsx（inlineStr，POI DataFormatter 可读）：表头 + 一行数据（列序同 ExcelTeacherHelper.template）"""
    headers = ["账号(必填)", "姓名(必填)", "角色", "手机号", "工号", "性别", "任教学科",
               "职称", "职务", "教龄", "入职年月", "班主任所带班级", "简介"]

    def cell_xml(col, val):
        return f'<c r="{chr(65 + col)}" t="inlineStr"><is><t>{val}</t></is></c>'

    rows_xml = ""
    for r, vals in enumerate([headers, row], start=1):
        cells = "".join(cell_xml(c, v) for c, v in enumerate(vals))
        rows_xml += f'<row r="{r}">{cells}</row>'
    sheet = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
             '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
             f'<sheetData>{rows_xml}</sheetData></worksheet>')
    ct = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
          '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
          '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
          '<Default Extension="xml" ContentType="application/xml"/>'
          '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
          '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
          '</Types>')
    rels = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
            '</Relationships>')
    wb = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
          '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
          'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
          '<sheets><sheet name="教师导入" sheetId="1" r:id="rId1"/></sheets></workbook>')
    wb_rels = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
               '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
               '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>'
               '</Relationships>')
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", ct)
        z.writestr("_rels/.rels", rels)
        z.writestr("xl/workbook.xml", wb)
        z.writestr("xl/_rels/workbook.xml.rels", wb_rels)
        z.writestr("xl/worksheets/sheet1.xml", sheet)
    return buf.getvalue()


def main():
    stamp = str(int(time.time()))
    admin = login("admin", "admin123")["token"]

    print("== ① 管理员建号口径（≥8）==")
    st, _ = call_raw("POST", "/api/admin/user", admin,
                     {"username": "qa_pw_a" + stamp, "password": "Ab1", "realName": "短密验收", "role": "TEACHER"})
    check("建号 <8 位被拒(400)", st == 400, f"status={st}")
    st, body = call_raw("POST", "/api/admin/user", admin,
                        {"username": "qa_pw_a" + stamp, "password": "QaTest123", "realName": "强密验收", "role": "TEACHER"})
    check("建号 ≥8 位通过", st == 200, f"status={st} {body.get('message', '')}")
    uid = body.get("data", {}).get("userId")

    print("== ② 首登强制改密（建号路径）==")
    lg = login("qa_pw_a" + stamp, "QaTest123")
    check("登录响应 mustChangePassword=true", lg["user"].get("mustChangePassword") is True,
          f"user={json.dumps(lg['user'], ensure_ascii=False)}")
    tk = lg["token"]
    st, me = call_raw("GET", "/api/auth/me", tk)
    check("/me 回带 mustChangePassword", st == 200 and me["data"].get("mustChangePassword") is True)
    st, biz = call_raw("GET", "/api/meta/terms", tk)
    check("业务接口被拦(403)", st == 403, f"status={st}")
    check("拦截文案含「修改初始密码」", "修改初始密码" in json.dumps(biz, ensure_ascii=False))

    print("== ③ 改密闭环 ==")
    st, _ = call_raw("PUT", "/api/auth/password", tk,
                     {"oldPassword": "WrongOld9", "newPassword": "QaTest456"})
    check("旧密码错被拒(400)", st == 400, f"status={st}")
    st, _ = call_raw("PUT", "/api/auth/password", tk,
                     {"oldPassword": "QaTest123", "newPassword": "Ab1"})
    check("新密码 <8 位被拒(400)", st == 400, f"status={st}")
    st, body = call_raw("PUT", "/api/auth/password", tk,
                        {"oldPassword": "QaTest123", "newPassword": "QaTest456"})
    new_token = (body.get("data") or {}).get("token", "")
    check("改密成功并换发新 token", st == 200 and new_token and new_token != tk, f"status={st}")
    st, _ = call_raw("GET", "/api/meta/terms", new_token)
    check("新 token 业务恢复(200)", st == 200, f"status={st}")
    lg2 = login("qa_pw_a" + stamp, "QaTest456")
    check("重登 mustChangePassword=false", lg2["user"].get("mustChangePassword") is False,
          f"user={json.dumps(lg2['user'], ensure_ascii=False)}")

    print("== ④ 管理员重置密码路径 ==")
    st, _ = call_raw("PUT", f"/api/admin/user/{uid}/password", admin, {"password": "Ab1"})
    check("重置 <8 位被拒(400)", st == 400, f"status={st}")
    st, _ = call_raw("PUT", f"/api/admin/user/{uid}/password", admin, {"password": "Reset9876"})
    check("重置 ≥8 位通过", st == 200, f"status={st}")
    lg3 = login("qa_pw_a" + stamp, "Reset9876")
    check("重置后登录 mustChangePassword=true", lg3["user"].get("mustChangePassword") is True)
    st, _ = call_raw("GET", "/api/meta/terms", lg3["token"])
    check("重置后业务被拦(403)", st == 403, f"status={st}")

    print("== ⑤ 批量导入初始密码路径 ==")
    xlsx = build_teacher_xlsx(["qa_pw_b" + stamp, "导入验收", "教师"])
    boundary = "----aischoolboundary7d1a2c"
    parts = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="t.xlsx"\r\n'
             f'Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n'
             ).encode() + xlsx + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(BASE + "/api/admin/teacher/import", method="POST", data=parts)
    req.add_header("Authorization", "Bearer " + admin)
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    with urllib.request.urlopen(req) as r:
        imp = json.loads(r.read())["data"]
    check("导入 inserted=1", imp.get("inserted") == 1, f"inserted={imp.get('inserted')} errors={imp.get('errors')}")
    lg4 = login("qa_pw_b" + stamp, "Shishi@2026")
    check("初始密码登录 mustChangePassword=true", lg4["user"].get("mustChangePassword") is True,
          f"user={json.dumps(lg4['user'], ensure_ascii=False)}")

    print("== ⑥ 清理 ==")
    if uid:
        st, _ = call_raw("DELETE", f"/api/admin/user/{uid}", admin)
        check("清理建号账号", st == 200, f"status={st}")
    st, body = call_raw("GET", "/api/admin/user/list?keyword=qa_pw_b" + stamp, admin)
    imp_uid = next((r["id"] for r in body["data"]["records"]), None)
    if imp_uid:
        st, _ = call_raw("DELETE", f"/api/admin/user/{imp_uid}", admin)
        check("清理导入账号", st == 200, f"status={st}")

    print(f"\nRESULT: {'PASS' if FAIL == 0 else 'FAIL'}  pass={PASS} fail={FAIL}")
    sys.exit(0 if FAIL == 0 else 1)


if __name__ == "__main__":
    main()
