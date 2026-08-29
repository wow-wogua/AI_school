// EP 桌面页体检：1440 宽截图 scores/comments/evaluate/summary/comprehensive/timeline/honor/activity
import { mkdirSync } from 'node:fs'

const SHOTS = 'D:/srp_project/AI_school/report-web/shots'
mkdirSync(SHOTS, { recursive: true })
const BASE = 'http://localhost:5173'
const PAGES = ['scores', 'comments', 'evaluate', 'summary', 'comprehensive', 'timeline', 'honor', 'activity']

export default async function run(page) {
  const out = {}
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto(`${BASE}/#/login`)
  await page.fill('input[placeholder="用户名"]', 'litao')
  await page.fill('input[placeholder="密码"]', 'aischool123')
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })

  for (const p of PAGES) {
    await page.goto(`${BASE}/#/${p}`)
    await page.waitForTimeout(900)
    // 成绩页量表格几何：卡片宽 vs 表格宽、左偏移
    if (p === 'scores') {
      out.tableGeom = await page.evaluate(() => {
        const card = document.querySelector('.el-card')
        const tbl = document.querySelector('.el-table')
        if (!card || !tbl) return { err: 'no card/table' }
        const c = card.getBoundingClientRect(), t = tbl.getBoundingClientRect()
        return {
          cardW: Math.round(c.width), tblW: Math.round(t.width),
          tblLeftInCard: Math.round(t.left - c.left),
          cardRightGap: Math.round(c.right - t.right),
          headerText: card.querySelector('.el-card__header')?.textContent?.slice(0, 60),
        }
      })
    }
    await page.screenshot({ path: `${SHOTS}/ep-${p}.png` })
  }
  return out
}
