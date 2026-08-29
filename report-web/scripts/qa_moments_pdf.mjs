// #12 真实链路验证：登录 → 查陈小华微光 → 生成报告 → 轮询任务结果
import { mkdirSync, writeFileSync } from 'node:fs'

const SHOTS = 'D:/srp_project/AI_school/report-web/shots'
mkdirSync(SHOTS, { recursive: true })
const BASE = 'http://localhost:5173'

export default async function run(page) {
  const out = {}
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`${BASE}/#/login`)
  await page.fill('input[placeholder="用户名"]', 'litao')
  await page.fill('input[placeholder="密码"]', 'aischool123')
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })

  // 登录后取 token，页内 fetch 复用同一套头
  const token = await page.evaluate(() => localStorage.getItem('token'))
  const hdrs = { 'Content-Type': 'application/json' }
  if (token) hdrs.Authorization = `Bearer ${token}`
  const j = async (url, opt) => {
    const r = await page.evaluate(async ({ u, o }) => {
      const res = await fetch(u, o)
      return { status: res.status, body: await res.json() }
    }, { u: `${BASE}${url}`, o: { headers: hdrs, ...opt } })
    return r
  }

  // 当前学期
  const terms = await j('/api/meta/terms')
  const cur = terms.body.data?.find((t) => t.isCurrent === 1) ?? terms.body.data?.[0]
  out.term = cur && { id: cur.id, name: cur.name }

  // 陈小华(id=1)的微光
  const mo = await j('/api/moment/student?studentId=1')
  out.moments = (mo.body.data ?? []).map((m) => ({
    id: m.id, sceneTag: m.sceneTag, hasPhoto: !!(m.photoUrl), createTime: m.createTime,
  }))

  // 触发报告生成
  const gen = await j('/api/report/generate', { method: 'POST', body: JSON.stringify({ studentId: 1, termId: cur.id }) })
  out.gen = { status: gen.status, body: gen.body }
  const taskId = gen.body.data?.id ?? gen.body.data?.taskId
  if (!taskId) return out

  // 轮询任务（渲染含 fork JVM + Playwright，最长等 240s）
  for (let i = 0; i < 48; i++) {
    await page.waitForTimeout(5000)
    const t = await j(`/api/report/task/${taskId}`)
    const d = t.body.data ?? {}
    out.lastTask = { status: t.status, state: d.status, fileName: d.fileName, error: d.errorMsg }
    if (['SUCCESS', 'DONE', 'COMPLETED', 'FAILED'].includes(d.status)) break
  }
  writeFileSync(`${SHOTS}/qa_moments_pdf.json`, JSON.stringify(out, null, 2))
  return out
}
