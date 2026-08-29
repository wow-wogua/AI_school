// 拉 /api/report/data/1 与 /api/report/list，把 JSON 落盘供 fitz 复核
import { writeFileSync } from 'node:fs'

const BASE = 'http://localhost:5173'

export default async function run(page) {
  const out = {}
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`${BASE}/#/login`)
  await page.fill('input[placeholder="用户名"]', 'litao')
  await page.fill('input[placeholder="密码"]', 'aischool123')
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })

  const token = await page.evaluate(() => localStorage.getItem('token'))
  const hdrs = {}
  if (token) hdrs.Authorization = `Bearer ${token}`
  const j = async (url) => page.evaluate(async ({ u, o }) => {
    const res = await fetch(u, o)
    return { status: res.status, body: await res.json() }
  }, { u: `${BASE}${url}`, o: { headers: hdrs } })

  const data = await j('/api/report/data/1?termId=2')
  const d = data.body.data ?? {}
  out.momentsCount = Array.isArray(d.moments) ? d.moments.length : null
  out.momentSample = (d.moments ?? []).slice(0, 6).map((m) => ({
    date: m.date, sceneTag: m.sceneTag, noteLen: (m.note ?? '').length,
    photoPrefix: (m.photo ?? '').slice(0, 30), photoLen: (m.photo ?? '').length,
  }))
  writeFileSync('D:/srp_project/AI_school/report-renderer/target/real_data_1.json',
    JSON.stringify(d))

  const list = await j('/api/report/list?classId=1&termId=2')
  out.list = (list.body.data?.records ?? list.body.data ?? []).slice?.(0, 3)
  const rid = out.list?.[0]?.reportId
  if (rid) {
    // 拉真实 PDF 落盘，供 fitz 验页数与照片
    const pdf = await page.evaluate(async ({ u, o }) => {
      const res = await fetch(u, o)
      const buf = await res.arrayBuffer()
      let bin = ''
      const bytes = new Uint8Array(buf)
      for (let i = 0; i < bytes.length; i += 8192)
        bin += String.fromCharCode(...bytes.subarray(i, i + 8192))
      return { status: res.status, b64: btoa(bin), size: bytes.length }
    }, { u: `${BASE}/api/report/file/${rid}`, o: { headers: hdrs } })
    out.pdf = { status: pdf.status, size: pdf.size }
    writeFileSync('D:/srp_project/AI_school/report-renderer/target/real_1.pdf',
      Buffer.from(pdf.b64, 'base64'))
  }
  return out
}
