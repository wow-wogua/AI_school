// 教师照片上传链路：登录 litao → POST /me/photo（两次，验旧对象清理）→ GET photo 验字节
import { readFileSync } from 'node:fs'

const BASE = 'http://localhost:5173'
const JPG = new Uint8Array(readFileSync('D:/srp_project/AI_school/report-web/shots/_pf_test.jpg'))

export default async function run(page) {
  const out = {}
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`${BASE}/#/login`)
  await page.fill('input[placeholder="用户名"]', 'litao')
  await page.fill('input[placeholder="密码"]', 'aischool123')
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })

  const token = await page.evaluate(() => localStorage.getItem('token'))
  const hdrs = { Authorization: `Bearer ${token}` }

  const upload = () => page.evaluate(async ({ u, bytes }) => {
    const fd = new FormData()
    fd.append('photo', new File([bytes], 'p.jpg', { type: 'image/jpeg' }))
    const r = await fetch(u, { method: 'POST', headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }, body: fd })
    return { status: r.status, body: await r.json() }
  }, { u: `${BASE}/api/profile/me/photo`, bytes: JPG })

  const up1 = await upload()
  out.up1 = { status: up1.status, photoUrl: up1.body.data?.photoUrl }
  const up2 = await upload()
  out.up2 = { status: up2.status, photoUrl: up2.body.data?.photoUrl }

  // 拉当前照片验字节（JPEG 头 FFD8）
  out.photo = await page.evaluate(async ({ u, t }) => {
    const r = await fetch(u, { headers: { Authorization: `Bearer ${t}` } })
    const buf = new Uint8Array(await r.arrayBuffer())
    return { status: r.status, len: buf.length, isJpeg: buf[0] === 0xff && buf[1] === 0xd8 }
  }, { u: out.up2.photoUrl, t: token })
  return out
}
