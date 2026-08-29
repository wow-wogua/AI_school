// 复现电脑端（1280x800）各页重叠问题
import { mkdirSync } from 'node:fs'

const SHOTS = 'D:/srp_project/AI_school/report-web/shots'
mkdirSync(SHOTS, { recursive: true })

export default async function run(page, ui) {
  await page.setViewportSize({ width: 1280, height: 800 })
  await page.goto('http://localhost:5173/#/login')
  await page.waitForSelector('input[placeholder="用户名"]', { timeout: 15000 })
  await page.fill('input[placeholder="用户名"]', 'litao')
  await page.fill('input[placeholder="密码"]', 'aischool123')
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })
  await page.waitForTimeout(800)

  await page.screenshot({ path: `${SHOTS}/d1-home.png` })
  await page.screenshot({ path: `${SHOTS}/d1-home-full.png`, fullPage: true })
  await page.goto('http://localhost:5173/#/class')
  await page.waitForSelector('.class-pick', { timeout: 15000 })
  await page.waitForTimeout(500)
  await page.screenshot({ path: `${SHOTS}/d2-class.png` })
  await page.goto('http://localhost:5173/#/feed')
  await page.waitForSelector('.chips', { timeout: 15000 })
  await page.waitForTimeout(500)
  await page.screenshot({ path: `${SHOTS}/d3-feed.png` })
  return ['desktop shots ok']
}
