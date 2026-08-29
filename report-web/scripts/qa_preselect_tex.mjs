// 预选打通 + 卡片底纹 + 校园剪影 验证
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

  // 学生详情 → 日常评价：预选陈小华
  await page.goto(`${BASE}/#/student/1`)
  await page.waitForSelector('.grid .g-item', { timeout: 10000 })
  await page.waitForTimeout(600)
  await page.screenshot({ path: `${SHOTS}/pre-student.png` })
  const gItems = page.locator('.grid .g-item')
  await gItems.nth(1).click()   // 日常评价
  await page.waitForURL('**/evaluate**', { timeout: 8000 })
  await page.waitForSelector('.el-table', { timeout: 10000 })
  await page.waitForTimeout(800)
  out.evalUrl = page.url()
  out.evalSelected = await page.evaluate(() =>
    document.querySelectorAll('.toolbar .el-select input')[1]?.value)
  out.evalRows = await page.evaluate(() => document.querySelectorAll('.el-table__row').length)
  await page.screenshot({ path: `${SHOTS}/pre-evaluate.png` })

  // 学生详情 → 成绩：预选班级 + 该生行高亮
  await page.goto(`${BASE}/#/student/1`)
  await page.waitForSelector('.grid .g-item', { timeout: 10000 })
  await page.waitForTimeout(400)
  await gItems.nth(0).click()   // 成绩
  await page.waitForURL('**/scores**', { timeout: 8000 })
  try {
    await page.waitForSelector('.el-table__row', { timeout: 8000 })
    out.scoreHl = await page.evaluate(() => {
      const hl = document.querySelector('.el-table .hl-row')
      return hl ? hl.querySelector('td:nth-child(2)').textContent : 'no-hl'
    })
  } catch { out.scoreHl = 'no-rows' }
  await page.screenshot({ path: `${SHOTS}/pre-scores.png` })

  // 学生详情 → 时间轴：预选陈小华
  await page.goto(`${BASE}/#/student/1`)
  await page.waitForSelector('.grid .g-item', { timeout: 10000 })
  await page.waitForTimeout(400)
  await gItems.nth(7).click()   // 时间轴
  await page.waitForURL('**/timeline**', { timeout: 8000 })
  await page.waitForTimeout(1000)
  out.tlSelected = await page.evaluate(() =>
    document.querySelectorAll('.toolbar .el-select input')[1]?.value)
  await page.screenshot({ path: `${SHOTS}/pre-timeline.png` })

  // 底纹 + 剪影断言（首页/班级页）
  await page.goto(`${BASE}/#/`)
  await page.waitForSelector('.stats', { timeout: 8000 })
  await page.waitForTimeout(500)
  out.decor = await page.evaluate(() => {
    const bi = getComputedStyle(document.querySelector('.stats')).backgroundImage
    return {
      texOn: bi.includes('/textures/'),
      skyline: !!document.querySelector('.skyline'),
      barcode: !!document.querySelector('.barcode'),
    }
  })
  await page.screenshot({ path: `${SHOTS}/tex-home.png` })
  await page.goto(`${BASE}/#/class`)
  await page.waitForTimeout(1200)
  out.classTex = await page.evaluate(() => {
    const list = document.querySelector('.list')
    return list ? getComputedStyle(list).backgroundImage.includes('/textures/') : 'no-list'
  })
  await page.screenshot({ path: `${SHOTS}/tex-class.png` })
  return out
}
