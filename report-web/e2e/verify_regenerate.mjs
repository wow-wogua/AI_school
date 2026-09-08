// 【缺陷修复】报告成功后 UI 无生成入口：成功行应有「重新生成」+确认弹窗；未生成/失败行为不变。
// 断言链：成功行三按钮(重新生成/预览/下载) → 重新生成弹确认框 → 确认后 POST /api/report/generate →
//        任务到终态后列表仍显示最新成功行；取消则不发起请求。
// 运行：node e2e/verify_regenerate.mjs（需后端 8080 + vite 5173；写入只发生在初一(2)班学生，学生1 零污染）
import { chromium } from 'playwright'

const BASE = process.env.BASE_URL || 'http://localhost:5173'
const USER = { username: 'zhaolaoshi', password: 'aischool123' } // 初一(2)班班主任

let pass = 0, fail = 0
const check = (name, cond, detail = '') => {
  console.log(`  ${cond ? 'PASS' : 'FAIL'} ${name}  ${detail}`)
  cond ? pass++ : fail++
}

const browser = await chromium.launch()
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  await page.goto(BASE + '/#/login')
  await page.getByPlaceholder('用户名').fill(USER.username)
  await page.getByPlaceholder('密码').fill(USER.password)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL(BASE + '/#/')
  await page.goto(BASE + '/#/reports')
  await page.waitForSelector('.el-table__row', { timeout: 15000 })
  await page.waitForTimeout(500)

  // 1. 保证至少一行「成功」（没有则先走一次单份生成）
  const statusOf = async (i) =>
    (await page.locator('.el-table__row').nth(i).locator('.el-tag').innerText()).trim()
  let okRow = -1
  for (let i = 0; i < await page.locator('.el-table__row').count(); i++) {
    if ((await statusOf(i)) === '成功') { okRow = i; break }
  }
  if (okRow === -1) {
    console.log('  （无成功行，先单份生成一份……）')
    await page.locator('.el-table__row').first().getByRole('button', { name: '生成' }).click()
    await page.waitForTimeout(1000)
    for (let t = 0; t < 40; t++) {
      await page.waitForTimeout(3000)
      await page.reload()
      await page.waitForSelector('.el-table__row')
      if ((await statusOf(0)) === '成功') { okRow = 0; break }
    }
  }
  check('存在成功行（前置）', okRow !== -1, `row=${okRow}`)
  if (okRow === -1) throw new Error('前置失败：无法得到成功行')

  const row = page.locator('.el-table__row').nth(okRow)
  // 2. 成功行按钮齐全
  check('成功行有「重新生成」', await row.getByRole('button', { name: '重新生成' }).count() === 1)
  check('成功行仍有「预览」「下载」',
    (await row.getByRole('button', { name: '预览' }).count()) === 1
    && (await row.getByRole('button', { name: '下载' }).count()) === 1)

  // 3. 未生成行为不变（若有）：只有「生成」，无「预览/下载」
  for (let i = 0; i < await page.locator('.el-table__row').count(); i++) {
    if ((await statusOf(i)) === '未生成') {
      const r = page.locator('.el-table__row').nth(i)
      check('未生成行只有「生成」',
        (await r.getByRole('button', { name: '生成' }).count()) === 1
        && (await r.getByRole('button', { name: '预览' }).count()) === 0)
      break
    }
  }

  // 4. 取消路径：弹确认框 → 取消 → 不发请求
  await row.getByRole('button', { name: '重新生成' }).click()
  const box = page.locator('.el-message-box')
  await box.waitFor({ timeout: 5000 })
  check('弹出确认弹窗', await box.innerText().then((t) => t.includes('重新生成')))
  let fired = 0
  page.on('request', (r) => r.url().includes('/api/report/generate') && r.method() === 'POST' && fired++)
  await box.getByRole('button', { name: '取消' }).click()
  await page.waitForTimeout(800)
  check('取消后不发起生成请求', fired === 0, `fired=${fired}`)

  // 5. 确认路径：确认 → POST /api/report/generate 200 → 任务终态 → 列表刷新仍成功
  await page.locator('.el-table__row').nth(okRow).getByRole('button', { name: '重新生成' }).click()
  await box.waitFor({ timeout: 5000 })
  const respP = page.waitForResponse(
    (r) => r.url().includes('/api/report/generate') && r.request().method() === 'POST', { timeout: 15000 })
  await box.getByRole('button', { name: '确定' }).click()
  const resp = await respP
  const code = (await resp.json()).code
  check('确认后发起生成（code=0）', resp.status() === 200 && code === 0, `status=${resp.status()} code=${code}`)
  // 轮询任务终态（单份 ≤30s 口径，放宽到 90s；排队/渲染中继续等）
  let final = null
  for (let t = 0; t < 30; t++) {
    await page.waitForTimeout(3000)
    await page.reload()
    await page.waitForSelector('.el-table__row')
    const s = await statusOf(okRow)
    if (['成功', '失败', '部分失败'].includes(s)) { final = s; break }
  }
  check('重生成后列表仍为成功（最新版生效）', final === '成功', `status=${final}`)
} finally {
  await browser.close()
}

console.log(`\nRESULT: ${fail === 0 ? 'PASS' : 'FAIL'}  pass=${pass} fail=${fail}`)
process.exit(fail === 0 ? 0 : 1)
