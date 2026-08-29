// 元素加量批次：全页面截图（装饰落地 + 无破坏验证）
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

  const shots = [
    ['home', '/', '.feed', 800],
    ['class', '/class', '.mo-card, .stu', 1200],
    ['feed', '/feed', '.card', 800],
    ['notice', '/notice', '.group', 800],
    ['mine', '/mine', '.cells', 800],
    ['student', '/student/1', '.info', 1200],
    ['momentlist', '/moment', '.cell', 1200],
    ['timeline', '/timeline', '.page', 1500],   // EP 功能页（sub 壳内 el-card 顶条+光斑底）
    ['capture', '/moment/new', '.stu-search', 800],
  ]
  for (const [name, hash, wait, extra] of shots) {
    await page.goto(`${BASE}/#${hash}`)
    try { await page.waitForSelector(wait, { timeout: 10000 }) } catch { out[name] = 'wait-timeout:' + wait }
    await page.waitForTimeout(extra)
    await page.screenshot({ path: `${SHOTS}/dec-${name}.png` })
    out[name] = 'ok'
  }

  // 装饰存在性断言（DOM）
  await page.goto(`${BASE}/#/`)
  await page.waitForSelector('.stats', { timeout: 8000 })
  out.decor = await page.evaluate(() => {
    const cs = getComputedStyle(document.querySelector('.stats'), '::before')
    const hero = getComputedStyle(document.querySelector('.app-hero'), '::before')
    const foot = document.querySelector('.app-foot')
    return {
      tlBarHeight: cs.height,           // 3px
      heroOpacity: hero.opacity,        // .28
      footText: foot?.textContent?.trim(),
    }
  })
  return out
}
