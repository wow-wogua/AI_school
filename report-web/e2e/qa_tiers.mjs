// 三档视觉 QA：手机(<768) / 平板(768-1023) / 电脑(≥1024)，生产构建 http://localhost/
// 用法：node e2e/qa_tiers.mjs（cwd 必须是 report-web；需系统 Chrome）
import { chromium } from 'playwright'

const BASE = 'http://localhost'
const SHOTS = 'e2e/shots'
const TIERS = [
  { tag: 'phone', width: 390, height: 844 },
  { tag: 'pad', width: 820, height: 1180 },
  { tag: 'desk', width: 1440, height: 900 },
]
const browser = await chromium.launch({ channel: 'chrome' })
let fail = 0
for (const t of TIERS) {
  const page = await browser.newPage({ viewport: { width: t.width, height: t.height } })
  const errors = []
  page.on('pageerror', (e) => errors.push(String(e)))
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)
  await page.screenshot({ path: `${SHOTS}/qa3_${t.tag}_login.png`, fullPage: false })
  const hScroll = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1)
  console.log(`${t.tag} login 横向溢出: ${hScroll ? 'YES(BAD)' : 'no'} ${errors.length ? 'JS错误:' + errors.join('|') : ''}`)
  if (hScroll || errors.length) fail++
  await page.locator('input').nth(0).fill('admin')
  await page.locator('input[type=password]').fill('admin123')
  await page.getByRole('button').click()
  await page.waitForURL('**/', { timeout: 10000 })
  await page.waitForTimeout(1200)
  await page.screenshot({ path: `${SHOTS}/qa3_${t.tag}_home.png`, fullPage: false })
  const h2 = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1)
  console.log(`${t.tag} home  横向溢出: ${h2 ? 'YES(BAD)' : 'no'}`)
  if (h2) fail++
  await page.close()
}
await browser.close()
console.log(fail === 0 ? 'TIERS PASS' : `TIERS FAIL(${fail})`)
process.exitCode = fail === 0 ? 0 : 1
