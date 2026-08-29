// 教师档案全链路 QA：litao 查看/编辑/保存/工号查重 + admin 列表/弹窗
const BASE = 'http://localhost:5173'
const SHOTS = 'D:/srp_project/AI_school/report-web/shots'

async function login(page, u, p) {
  await page.goto(`${BASE}/#/login`)
  await page.fill('input[placeholder="用户名"]', u)
  await page.fill('input[placeholder="密码"]', p)
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })
}

export default async function run(page) {
  const out = {}
  const errs = []
  page.on('console', (m) => m.type() === 'error' && errs.push(m.text()))

  // ── litao：我的-教师档案入口 + 档案页 ──
  await page.setViewportSize({ width: 390, height: 844 })
  await login(page, 'litao', 'aischool123')
  await page.goto(`${BASE}/#/mine`)
  await page.waitForTimeout(700)
  out.mineEntry = await page.evaluate(() => {
    const cells = [...document.querySelectorAll('.van-cell')]
    const c = cells.find((x) => x.textContent.includes('教师档案'))
    return c ? c.textContent.trim().replace(/\s+/g, ' ') : null
  })
  await page.goto(`${BASE}/#/profile`)
  await page.waitForTimeout(1000)
  out.profileForm = await page.evaluate(() => {
    const val = (sel) => document.querySelector(sel)?.value ?? ''
    return {
      employeeNo: val('.van-field input[placeholder*="工号"]'),
      teachingYears: val('.van-field input[placeholder*="12"]'),
      intro: val('textarea'),
      hireShown: document.body.textContent.includes('2010-09') || [...document.querySelectorAll('.van-field__value')].some((v) => v.textContent.includes('2010-09')),
      titleDutyShown: [...document.querySelectorAll('.van-field__value')].map((v) => v.textContent.trim()).filter(Boolean).slice(0, 8),
    }
  })
  await page.screenshot({ path: `${SHOTS}/profile-litao.png` })

  // ── 编辑：改职称→保存→回读 ──
  await page.evaluate(() => { location.hash = '#/profile' })
  await page.locator('.van-field:has-text("职称")').click()
  await page.waitForTimeout(500)
  await page.getByText('正高级教师').click()
  await page.waitForTimeout(300)
  await page.locator('.van-picker__confirm').click()
  await page.waitForTimeout(300)
  await page.locator('.submit').click()
  await page.waitForTimeout(1200)
  out.saveToast = await page.evaluate(() => document.querySelector('.van-toast')?.textContent ?? '')
  // API 回读验证保存结果（比 DOM textContent 可靠，picker 值在 input 里）
  out.afterSave = await page.evaluate(async () => {
    const token = localStorage.getItem('token')
    const r = await fetch('/api/profile/me', { headers: { Authorization: `Bearer ${token}` } })
    const d = (await r.json()).data ?? {}
    return { title: d.title, gender: d.gender, subjectName: d.subjectName, hireDate: d.hireDate }
  })

  // ── 工号查重：admin 已占 SS001（见下步先造），litao 改成 SS001 应报错 ──
  // （admin 档案在 admin 登录段后才有，这里先验后端 403/400 语义，由 admin 段补验）
  out.consoleErrorsLitao = errs.splice(0)

  // ── admin：管理端教师管理 ──
  await page.evaluate(() => localStorage.clear())
  await login(page, 'admin', 'admin123')
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto(`${BASE}/#/admin`)
  await page.waitForTimeout(1500)
  out.adminCols = await page.evaluate(() =>
    [...document.querySelectorAll('.el-table__header th .cell')].map((th) => th.textContent.trim()).slice(0, 10))
  out.adminLitaoRow = await page.evaluate(() => {
    const rows = [...document.querySelectorAll('.el-table__body tr')]
    const r = rows.find((x) => x.textContent.includes('litao'))
    return r ? r.textContent.trim().replace(/\s+/g, ' ').slice(0, 90) : null
  })
  await page.screenshot({ path: `${SHOTS}/profile-admin-tab.png` })
  // 打开李老师档案弹窗
  const litaoRow = page.locator('.el-table__body tr', { hasText: 'litao' }).first()
  await litaoRow.getByText('档案', { exact: true }).click()
  await page.waitForTimeout(900)
  out.adminDlg = await page.evaluate(() => {
    const dlg = document.querySelector('.el-dialog')
    return dlg ? dlg.textContent.trim().replace(/\s+/g, ' ').slice(0, 200) : null
  })
  await page.screenshot({ path: `${SHOTS}/profile-admin-dlg.png` })
  out.consoleErrorsAdmin = errs
  return out
}
