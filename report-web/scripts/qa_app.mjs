// 阶段1 移动端自测：登录 + 五页截图（390x844 视口）
// 用法: node <skill>/browser.mjs http://localhost:5173/#/login --script scripts/qa_app.mjs
import { mkdirSync } from 'node:fs'

const SHOTS = 'D:/srp_project/AI_school/report-web/shots'
mkdirSync(SHOTS, { recursive: true })

export default async function run(page, ui) {
  await page.setViewportSize({ width: 390, height: 844 })
  const out = []

  await page.waitForSelector('input[placeholder="用户名"]', { timeout: 15000 })
  await page.screenshot({ path: `${SHOTS}/01-login.png` })
  out.push('01-login ok')

  // 登录（litao 班主任：初一(1)班，数据全）
  await page.fill('input[placeholder="用户名"]', 'litao')
  await page.fill('input[placeholder="密码"]', 'aischool123')
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })
  await page.waitForTimeout(800)   // 等动态流加载
  await page.screenshot({ path: `${SHOTS}/02-home.png`, fullPage: true })
  out.push('02-home ok')

  const routes = [
    ['class', '.class-pick'],
    ['notice', '.group'],
    ['mine', '.cells'],
    ['feed', '.chips'],
  ]
  for (const [name, waitSel] of routes) {
    await page.goto(`http://localhost:5173/#/${name}`)
    await page.waitForSelector(waitSel, { timeout: 15000 })
    await page.waitForTimeout(800)
    await page.screenshot({ path: `${SHOTS}/03-${name}.png`, fullPage: true })
    out.push(`03-${name} ok`)
  }
  return out
}
