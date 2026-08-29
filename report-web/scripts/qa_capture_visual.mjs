// 拍照页吸底提交+学生搜索框 视觉确认
import { mkdirSync } from 'node:fs'

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

  await page.goto(`${BASE}/#/moment/new`)
  await page.waitForSelector('.stu-search', { timeout: 10000 })
  await page.waitForSelector('.stu >> nth=0', { timeout: 10000 })

  // 首屏即应看到吸底提交栏（长学生列表前）
  out.submitVisibleAtTop = await page.evaluate(() => {
    const bar = document.querySelector('.submit-bar')
    if (!bar) return false
    const r = bar.getBoundingClientRect()
    return r.bottom <= window.innerHeight && r.height > 40
  })
  await page.screenshot({ path: `${SHOTS}/m3-capture-sticky.png` })

  // 搜索过滤：输「陈」只剩匹配项
  await page.fill('.stu-search input', '陈')
  await page.waitForTimeout(300)
  out.filterChen = await page.locator('.stu').count()
  out.filterNames = await page.locator('.stu .name').allTextContents()
  await page.fill('.stu-search input', '')
  await page.waitForTimeout(300)
  out.filterCleared = await page.locator('.stu').count()
  return out
}
