// ②③ 修复复测：成绩表格列分满+居中；honor hint 对比度
const BASE = 'http://localhost:5173'

export default async function run(page) {
  const out = {}
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto(`${BASE}/#/login`)
  await page.fill('input[placeholder="用户名"]', 'litao')
  await page.fill('input[placeholder="密码"]', 'aischool123')
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })

  // 成绩页：列几何
  await page.goto(`${BASE}/#/scores`)
  await page.waitForTimeout(1200)
  out.scoreCols = await page.evaluate(() => {
    const card = document.querySelector('.el-card')
    const tbl = document.querySelector('.el-table')
    const ths = [...document.querySelectorAll('.el-table__header th .cell')]
    const c = card?.getBoundingClientRect(), t = tbl?.getBoundingClientRect()
    return {
      cardW: c && Math.round(c.width), tblW: t && Math.round(t.width),
      cols: ths.map((th) => ({ label: th.textContent.trim(), w: Math.round(th.parentElement.getBoundingClientRect().width) })),
      lastColRight: ths.length ? Math.round(ths[ths.length - 1].parentElement.getBoundingClientRect().right) : 0,
      tblRight: t && Math.round(t.right),
    }
  })
  await page.screenshot({ path: 'D:/srp_project/AI_school/report-web/shots/ep-scores-fixed.png' })

  // honor 页：hint 颜色
  await page.goto(`${BASE}/#/honor`)
  await page.waitForTimeout(900)
  out.honorHint = await page.evaluate(() => {
    const h = document.querySelector('.hint')
    return h ? { color: getComputedStyle(h).color, text: h.textContent.slice(0, 40) } : null
  })
  return out
}
