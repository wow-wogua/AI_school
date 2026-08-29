// 班级页图书馆底纹 + 学生行白玻璃片 + 首页 74% 白纱 验证
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

  // 班级页：图书馆底纹 + 玻璃片学生行
  await page.goto(`${BASE}/#/class`)
  await page.waitForSelector('.list .stu', { timeout: 10000 })
  await page.waitForTimeout(600)
  out.cls = await page.evaluate(() => {
    const list = document.querySelector('.list')
    const stu = document.querySelector('.stu')
    return {
      libTex: getComputedStyle(list).backgroundImage.includes('1507842217343'),
      chip: getComputedStyle(stu).backgroundColor,
      radius: getComputedStyle(stu).borderRadius,
      rows: document.querySelectorAll('.stu').length,
    }
  })
  await page.screenshot({ path: `${SHOTS}/tex-class-v2.png` })

  // 首页：74% 白纱回归
  await page.goto(`${BASE}/#/`)
  await page.waitForSelector('.stats', { timeout: 8000 })
  await page.waitForTimeout(500)
  await page.screenshot({ path: `${SHOTS}/tex-home-v2.png` })
  return out
}
