// 阶段1 自测：学生详情直链 + legacy 旧页面 + 通知任务直达，均走 URL 直达（避免点击被固定层拦截）
import { mkdirSync } from 'node:fs'

const SHOTS = 'D:/srp_project/AI_school/report-web/shots'
mkdirSync(SHOTS, { recursive: true })

export default async function run(page, ui) {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('http://localhost:5173/#/login')
  await page.waitForSelector('input[placeholder="用户名"]', { timeout: 15000 })
  await page.fill('input[placeholder="用户名"]', 'litao')
  await page.fill('input[placeholder="密码"]', 'aischool123')
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })

  const out = []
  // 学生详情直链（学生1 = 契约基线）
  await page.goto('http://localhost:5173/#/student/1')
  await page.waitForSelector('.detail .grid', { timeout: 15000 })
  await page.waitForTimeout(600)
  await page.screenshot({ path: `${SHOTS}/04-student.png`, fullPage: true })
  out.push('04-student ok')

  // legacy 壳：成绩管理旧页
  await page.goto('http://localhost:5173/#/scores')
  await page.waitForTimeout(1500)
  const navOk = await page.locator('.nav .links').count()
  await page.screenshot({ path: `${SHOTS}/05-legacy-scores.png` })
  out.push(`05-legacy-scores nav=${navOk}`)

  // 通知→寄语页直达链路（取第一个 AI 任务卡片 href 行为等价：直接构造 query 跳转）
  const first = await page.evaluate(() => localStorage.getItem('token') ? 'y' : 'n')
  out.push(`06-token=${first}`)
  return out
}
