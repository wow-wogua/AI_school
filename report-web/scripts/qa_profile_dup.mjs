// 工号查重：admin 抢注 SS002 → litao 再存 SS002 应 400；随后还原 litao 演示档案 + 验收截图
const BASE = 'http://localhost:5173'

async function login(page, u, p) {
  await page.goto(`${BASE}/#/login`)
  await page.fill('input[placeholder="用户名"]', u)
  await page.fill('input[placeholder="密码"]', p)
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })
}

export default async function run(page) {
  const out = {}
  await page.setViewportSize({ width: 390, height: 844 })

  // admin 先占 SS002
  await login(page, 'admin', 'admin123')
  out.adminGrab = await page.evaluate(async () => {
    const r = await fetch('/api/profile/me', {
      method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('token')}` },
      body: JSON.stringify({ employeeNo: 'SS002' }),
    })
    return { status: r.status, body: await r.json() }
  })

  // litao 存 SS002 → 应 400 并提示占用者
  await page.evaluate(() => localStorage.clear())
  await login(page, 'litao', 'aischool123')
  out.litaoDup = await page.evaluate(async () => {
    const r = await fetch('/api/profile/me', {
      method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('token')}` },
      body: JSON.stringify({ employeeNo: 'SS002' }),
    })
    const b = await r.json()
    return { status: r.status, message: b.message }
  })

  // 还原 litao 演示档案（QA 改过职称）
  out.restore = await page.evaluate(async () => {
    const r = await fetch('/api/profile/me', {
      method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('token')}` },
      body: JSON.stringify({
        employeeNo: 'SS001', gender: '女', subjectId: 1, title: '高级教师', duty: '班主任',
        teachingYears: 16, hireDate: '2010-09-01',
        intro: '语文教师，深耕阅读与写作教学，相信每个孩子都值得被看见。',
      }),
    })
    return { status: r.status }
  })

  // 验收截图：档案页（含已传照片）
  await page.goto(`${BASE}/#/profile`)
  await page.waitForTimeout(1200)
  await page.screenshot({ path: 'D:/srp_project/AI_school/report-web/shots/profile-final.png' })
  return out
}
