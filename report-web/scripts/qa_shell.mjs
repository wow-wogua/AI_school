// 验证批次：①10 功能页挂 sub 新壳（导航条/旧壳退役）②成长总结精改 ③学校元素（照片带/水印/登录高清背景）
import { mkdirSync } from 'node:fs'

const SHOTS = 'D:/srp_project/AI_school/report-web/shots'
mkdirSync(SHOTS, { recursive: true })
const BASE = 'http://localhost:5173'

export default async function run(page) {
  const out = { pages: {} }

  // 登录页：背景应为 campus-bg + blur 3px
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`${BASE}/#/login`)
  await page.waitForSelector('input[placeholder="用户名"]', { timeout: 15000 })
  await page.waitForTimeout(400)
  out.login = await page.evaluate(() => {
    const bg = document.querySelector('.login-bg')
    if (!bg) return null
    const cs = getComputedStyle(bg)
    return { img: cs.backgroundImage.includes('campus-bg'), blur: cs.filter }
  })
  await page.screenshot({ path: `${SHOTS}/f2-login-m.png` })

  await page.fill('input[placeholder="用户名"]', 'litao')
  await page.fill('input[placeholder="密码"]', 'aischool123')
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })
  await page.waitForTimeout(600)

  // 逐页走查：sub 壳导航条 + 旧壳痕迹清零
  const routes = [
    ['/summary', '成长总结'], ['/scores', '成绩管理'], ['/reports', '成长报告'],
    ['/comments', '班主任寄语'], ['/activity', '活动管理'], ['/honor', '荣誉证书'],
    ['/timeline', '成长时间轴'], ['/evaluate', '日常评价'], ['/comprehensive', '综合素质'],
    ['/reports/1/preview', '报告预览'],
  ]
  for (const [path, title] of routes) {
    await page.goto(`${BASE}/#${path}`)
    await page.waitForTimeout(900)
    out.pages[path] = await page.evaluate((t) => {
      const nav = document.querySelector('.sub-nav')
      return {
        nav: !!nav,
        navTitle: nav?.querySelector('h1')?.textContent,
        titleOk: nav?.querySelector('h1')?.textContent === t,
        back: !!nav?.querySelector('.back'),
        legacy: !!document.querySelector('.nav, .links, .app-footer, .el-header'),
      }
    }, title)
  }

  // 成长总结：筛选三行 + 选学生交互 + AI 按钮态
  await page.goto(`${BASE}/#/summary`)
  await page.waitForSelector('.filter', { timeout: 10000 })
  await page.waitForTimeout(800)
  const cells = page.locator('.filter .van-cell')
  out.summary = { cells: await cells.count() }
  await cells.nth(1).click()                      // 学生行 → 弹层
  await page.waitForSelector('.stu-row', { timeout: 8000 })
  const rowCount = await page.locator('.stu-row').count()
  out.summary.stuRows = rowCount
  await page.screenshot({ path: `${SHOTS}/f2-summary-picker.png` })
  await page.locator('.stu-row').first().click()
  await page.waitForTimeout(400)
  out.summary.stuPicked = await page.evaluate(() => {
    const btn = document.querySelector('.act .van-button')
    return { label: [...document.querySelectorAll('.filter .van-cell__value')].map((e) => e.textContent?.trim()), disabled: btn?.classList.contains('van-button--disabled') }
  })
  await page.screenshot({ path: `${SHOTS}/f2-summary-m.png` })

  // 学校元素：班级页照片带 / 通知·我的 校徽水印 / 首页照片带
  await page.goto(`${BASE}/#/class`)
  await page.waitForSelector('.stu', { timeout: 10000 })
  out.classPano = await page.evaluate(() => {
    const img = document.querySelector('.hero .hero-photo')
    return { has: !!img, loaded: img ? img.complete && img.naturalWidth > 0 : false, h: img ? Math.round(img.getBoundingClientRect().height) : 0 }
  })
  await page.screenshot({ path: `${SHOTS}/f2-class-m.png` })

  out.mark = {}
  for (const p of ['notice', 'mine']) {
    await page.goto(`${BASE}/#/${p}`)
    await page.waitForTimeout(700)
    out.mark[p] = await page.evaluate(() => getComputedStyle(document.querySelector('.app-hero')).content)
  }
  await page.screenshot({ path: `${SHOTS}/f2-notice-m.png` })

  // 桌面视口抽查：成长总结 + 一个 EP 功能页（成绩）在新壳下的观感
  await page.setViewportSize({ width: 1280, height: 800 })
  await page.goto(`${BASE}/#/summary`)
  await page.waitForTimeout(900)
  await page.screenshot({ path: `${SHOTS}/f2-summary-d.png` })
  await page.goto(`${BASE}/#/scores`)
  await page.waitForTimeout(1200)
  await page.screenshot({ path: `${SHOTS}/f2-scores-d.png` })

  return out
}
