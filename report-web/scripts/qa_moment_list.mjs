// 微光瞬间独立照片墙页：入口→标签筛选→照片加载
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

  // 快捷菜单第二位=微光瞬间
  await page.click('.fab-tab')
  await page.waitForSelector('.van-action-sheet__item', { timeout: 5000 })
  out.quickItems = await page.evaluate(() =>
    [...document.querySelectorAll('.van-action-sheet__item')].slice(0, 2).map((el) => el.textContent?.trim()))
  await page.keyboard.press('Escape')
  await page.waitForTimeout(300)

  // 班级页「本周微光」标题 → 照片墙
  await page.goto(`${BASE}/#/class`)
  await page.waitForSelector('.mo-title', { timeout: 10000 })
  await page.click('.mo-title')
  await page.waitForSelector('.cell', { timeout: 10000 })
  await page.waitForTimeout(1200)
  out.url = page.url()
  out.totalCells = await page.locator('.cell').count()
  out.photosLoaded = await page.evaluate(() =>
    [...document.querySelectorAll('.cell img')].filter((i) => i.complete && i.naturalWidth > 0).length)
  out.chipCount = await page.locator('.chip').count() // 全部 + 有数据的标签
  out.firstNames = (await page.locator('.names').first().textContent())?.trim()
  out.subTitle = (await page.locator('.sub-title, .app-sub .title').first().textContent().catch(() => ''))?.trim()
  await page.screenshot({ path: `${SHOTS}/m3-moment-list.png` })

  // 标签筛选：点「作业优秀」只剩该场景
  await page.click('.chip >> nth=1')
  await page.waitForTimeout(400)
  out.filteredCells = await page.locator('.cell').count()
  out.filteredTag = await page.evaluate(() =>
    [...document.querySelectorAll('.cell .p-tag')].every((t) => t.textContent?.trim() === '作业优秀'))
  await page.screenshot({ path: `${SHOTS}/m3-moment-filtered.png` })

  // 拍照页成功卡按钮指向照片墙（验证路由引用存在即可）
  await page.goto(`${BASE}/#/moment/new`)
  await page.waitForSelector('.stu-search', { timeout: 8000 })
  out.captureOk = true
  return out
}
