// M6 E2E 冒烟：活动管理 / 荣誉证书 / 成长时间轴 三页
// 桌面(1440x900) + 手机(390x844) 两档：zhaolaoshi（初一(2)班班主任）
// 流程：登录 → 活动页选活动/录参与 → 荣誉页上传+确认 → 时间轴断言事件 → 每页无横向滚动 + 截图
// 运行：node e2e/verify_m6_web.mjs（需后端 8080 + vite 5173；系统 Chrome）
import { chromium } from 'playwright'
import fs from 'node:fs'

const BASE = process.env.BASE_URL || 'http://localhost:5173'
const USER = { username: 'zhaolaoshi', password: 'aischool123' }
const SHOTS = 'e2e/shots'
fs.mkdirSync(SHOTS, { recursive: true })

// 1x1 JPEG（与后端 verify_m6.py 同源）
const JPEG = Buffer.from(
  '/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwcJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPDIzNP/AABEIAAEAAQMBIgACEQEDEQH/xAAfAAABBQEBAQEBAQAAAAAAAAABAgMEBQYHCAkKC//EALUQAAIBAwMCBAMFBQQEAAABfQECAwAEEQUSITFBBhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6g4SFhoeIiYqSk5SVlpeYmZqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2drh4uPk5ebn6Onq8fLz9PX29/j5+v/aAAwDAQACEQMRAD8A/v4ooooA/9k=',
  'base64',
)

let pass = 0, fail = 0
const check = (name, cond, detail = '') => {
  console.log(`  ${cond ? 'PASS' : 'FAIL'} ${name}  ${detail}`)
  cond ? pass++ : fail++
}

async function noHScroll(page, tag) {
  const w = await page.evaluate(() => ({
    sw: document.documentElement.scrollWidth,
    iw: window.innerWidth,
  }))
  check(`${tag} 无横向滚动`, w.sw <= w.iw + 1, `scrollWidth=${w.sw} innerWidth=${w.iw}`)
}

async function login(page) {
  await page.goto(BASE + '/login')
  await page.getByPlaceholder('用户名').fill(USER.username)
  await page.getByPlaceholder('密码').fill(USER.password)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL(BASE + '/')
}

async function m6Flow(browser, { name, width, height, mobile }) {
  console.log(`\n===== ${name} ${width}x${height} =====`)
  const ctx = await browser.newContext({ viewport: { width, height }, isMobile: mobile, hasTouch: mobile })
  const page = await ctx.newPage()
  try {
    await login(page)
    check(`${name} 登录成功`, true)

    // ── 活动管理：列表 → 录参与 ──
    await page.getByRole('link', { name: '活动管理' }).click()
    await page.waitForURL(BASE + '/activity')
    await page.waitForSelector('.el-table__row', { timeout: 15000 })
    await noHScroll(page, `${name} 活动页`)
    await page.screenshot({ path: `${SHOTS}/m6-${name}-1-activity.png` })
    // 班主任看不到「新建活动」
    check(`${name} 班主任无新建活动按钮`, (await page.getByRole('button', { name: '新建活动' }).count()) === 0)
    // 点第一个活动 → 出现参与记录卡片 → 录参与
    await page.locator('.el-table__row').first().click()
    await page.getByRole('button', { name: '录参与' }).waitFor({ timeout: 8000 })
    await page.getByRole('button', { name: '录参与' }).click()
    const dlg = page.locator('.el-dialog').last()
    await dlg.waitFor({ state: 'visible' })
    await dlg.locator('.el-select').nth(1).click() // 学生（班级已默认）
    await page.locator('.el-select-dropdown__item:visible').first().click()
    await dlg.locator('.el-form-item', { hasText: '奖项' }).locator('input').first().fill('E2E优胜奖')
    await dlg.locator('.el-switch__core').first().click()
    await dlg.getByRole('button', { name: '保存' }).click()
    await page.waitForTimeout(1500)
    check(`${name} 录参与成功`, (await page.getByText('E2E优胜奖').count()) > 0)
    await page.screenshot({ path: `${SHOTS}/m6-${name}-2-signup.png` })

    // ── 荣誉证书：上传 → 表单 → 确认 ──
    await page.getByRole('link', { name: '荣誉证书' }).click()
    await page.waitForURL(BASE + '/honor')
    await page.waitForSelector('.el-select', { timeout: 8000 })
    await noHScroll(page, `${name} 荣誉页`)
    // 选第一个学生
    await page.locator('.el-select').nth(1).click() // 0=班级 1=学生
    await page.locator('.el-select-dropdown__item:visible').first().click()
    await page.waitForSelector('.el-table__row', { timeout: 8000 })
    await page.locator('input[type=file]').setInputFiles({ name: 'e2e.jpg', mimeType: 'image/jpeg', buffer: JPEG })
    await page.waitForTimeout(2500)
    const dlg2 = page.locator('.el-dialog').last()
    await dlg2.waitFor({ state: 'visible' })
    await dlg2.getByRole('textbox').first().fill('E2E绘画比赛优胜奖')
    // 无日期的荣誉不进时间轴（按学期过滤），必须填日期
    await dlg2.locator('.el-form-item', { hasText: '日期' }).locator('input').first().fill('2026-03-18')
    await dlg2.getByRole('button', { name: '保存' }).click()
    await page.waitForTimeout(1000)
    // 列表上直接确认（带能量币）
    const pendingRow = page.locator('.el-table__row', { hasText: '待确认' }).first()
    await pendingRow.getByRole('button', { name: '确认生效' }).click()
    const dlg3 = page.locator('.el-dialog').last()
    await dlg3.waitFor({ state: 'visible' })
    await dlg3.getByRole('textbox').first().fill('E2E绘画比赛优胜奖')
    await dlg3.locator('.el-input-number input').fill('10')
    await dlg3.getByRole('button', { name: '确认生效' }).click()
    await page.waitForTimeout(2000)
    check(`${name} 荣誉确认生效`, (await page.locator('.el-tag', { hasText: '已确认' }).count()) > 0)
    await page.screenshot({ path: `${SHOTS}/m6-${name}-3-honor.png` })

    // ── 成长时间轴 ──
    await page.getByRole('link', { name: '成长时间轴' }).click()
    await page.waitForURL(BASE + '/timeline')
    await page.waitForSelector('.el-select', { timeout: 8000 })
    await noHScroll(page, `${name} 时间轴页`)
    await page.locator('.el-select').nth(1).click() // 学生
    await page.locator('.el-select-dropdown__item:visible').first().click()
    await page.locator('.el-timeline-item').first().waitFor({ timeout: 8000 })
    const nItems = await page.locator('.el-timeline-item').count()
    const bodyText = await page.locator('.el-timeline').innerText()
    check(`${name} 时间轴事件≥3`, nItems >= 3, `n=${nItems}`)
    check(`${name} 时间轴含活动事件(荣获)`, bodyText.includes('荣获'), '')
    check(`${name} 时间轴含荣誉事件`, bodyText.includes('E2E绘画比赛优胜奖') || bodyText.includes('荣誉'), '')
    await page.screenshot({ path: `${SHOTS}/m6-${name}-4-timeline.png`, fullPage: false })
  } finally {
    await ctx.close()
  }
}

// 用系统 Chrome（channel），免下载 playwright chromium
const browser = await chromium.launch({ channel: 'chrome' })
try {
  await m6Flow(browser, { name: 'desktop', width: 1440, height: 900, mobile: false })
  await m6Flow(browser, { name: 'mobile', width: 390, height: 844, mobile: true })
} finally {
  await browser.close()
}
console.log(`\nRESULT: ${fail === 0 ? 'PASS' : 'FAIL'}  pass=${pass} fail=${fail}`)
process.exit(fail === 0 ? 0 : 1)
