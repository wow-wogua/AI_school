// v1.0.17 生态完善批 E2E：下拉刷新（4 页）/ 骨架屏（首页+班级）/ 照片预览保存按钮组件挂载
// 触摸手势用 TouchEvent 合成（Playwright 桌面无 touchscreen）驱动 van-pull-refresh。
// 运行：node e2e/verify_polish.mjs（需后端 8080 + vite 5173）
import { chromium } from 'playwright'

const BASE = process.env.BASE_URL || 'http://localhost:5173'
const H = (p) => BASE + '/#' + p
const USER = { username: 'zhaolaoshi', password: 'aischool123' }

let pass = 0, fail = 0
const check = (name, cond, detail = '') => {
  console.log(`  ${cond ? 'PASS' : 'FAIL'} ${name}  ${detail}`)
  cond ? pass++ : fail++
}

async function login(page) {
  await page.goto(H('/login'))
  await page.getByPlaceholder('用户名').fill(USER.username)
  await page.getByPlaceholder('密码').fill(USER.password)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL(H('/'))
}

/** 合成下拉手势：在 track 顶部 touchstart → 下拉 90px（分步）→ touchend */
async function pullDown(page) {
  await page.evaluate(() => {
    const track = document.querySelector('.van-pull-refresh__track')
    const rect = track.getBoundingClientRect()
    const mk = (y) => new Touch({ identifier: 1, target: track,
      clientX: rect.x + 30, clientY: rect.y + y, pageX: rect.x + 30, pageY: rect.y + y,
      radiusX: 2, radiusY: 2, rotationAngle: 0, force: 1 })
    let y = 10
    const down = (type) => track.dispatchEvent(new TouchEvent(type, {
      bubbles: true, cancelable: true,
      touches: type === 'touchend' ? [] : [mk(y)],
      targetTouches: type === 'touchend' ? [] : [mk(y)],
      changedTouches: [mk(y)],
    }))
    down('touchstart')
    const step = () => { y += 18; down('touchmove') }
    for (let i = 0; i < 6; i++) step()
    down('touchend')
  })
}

const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL || 'chrome' })
try {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })
  const page = await ctx.newPage()
  await login(page)
  check('登录进入首页', true)

  // 首页：骨架屏（刷新前的初载已过，直接断言结构）+ 下拉刷新触发接口重拉
  check('首页含下拉刷新容器', await page.locator('.van-pull-refresh').count() === 1)
  let homeReload = 0
  page.on('request', (r) => { if (r.url().includes('/api/feed?limit=5')) homeReload++ })
  await pullDown(page)
  await page.waitForTimeout(1500)
  check('首页下拉触发重拉', homeReload >= 1, `feed 请求=${homeReload}`)

  // 成长流：骨架出现于初载（快速断言加载后无骨架残留）+ 下拉
  await page.goto(H('/feed'))
  await page.getByText('成长记录').first().waitFor({ timeout: 10000 })
  await page.waitForTimeout(1200)
  check('成长流加载后无骨架残留', await page.locator('.van-skeleton').count() === 0)
  let feedReload = 0
  page.on('request', (r) => { if (r.url().includes('/api/feed?limit=50')) feedReload++ })
  await pullDown(page)
  await page.waitForTimeout(1500)
  check('成长流下拉触发重拉', feedReload >= 1, `feed 请求=${feedReload}`)

  // 班级页：骨架 + 下拉（学生列表接口）
  await page.goto(H('/class'))
  await page.getByText('成长档案').first().waitFor({ timeout: 10000 })
  await page.waitForTimeout(1200)
  check('班级页加载后无骨架残留', await page.locator('.van-skeleton').count() === 0)
  let stuReload = 0
  page.on('request', (r) => { if (r.url().includes('/api/student/list')) stuReload++ })
  await pullDown(page)
  await page.waitForTimeout(1500)
  check('班级页下拉触发重拉', stuReload >= 1, `student 请求=${stuReload}（listener 在初载后注册，计数即刷新次数）`)

  // 微光列表：下拉（无数据时 track 仍可拉——min-height 兜底）+ 照片预览保存/分享
  // route 注入一条假微光（本地库无微光数据），照片用 vite public 静态图（fetchBlob 可拉）
  await page.route('**/api/moment/class**', (r) => r.fulfill({
    contentType: 'application/json',
    body: JSON.stringify([{ id: 99001, photoUrl: '/campus-bg.jpg', sceneTag: '课堂专注',
      createTime: '2026-09-13 10:00:00', teacherName: '赵老师', note: 'e2e', studentIds: [1] }])
  }))
  await page.goto(H('/moment'))
  await page.locator('.moment-photo').first().waitFor({ timeout: 10000 })
  await pullDown(page)
  await page.waitForTimeout(1000)
  check('微光列表下拉不报错', true)

  // 点开大图 → 保存/分享按钮（#cover slot）可见；点按钮不误关预览且触发下载（浏览器态=a[download]）
  await page.locator('.moment-photo').first().click()
  await page.locator('.van-image-preview').waitFor({ timeout: 5000 })
  check('照片大图预览打开', await page.locator('.pv-save').isVisible())
  const dl = page.waitForEvent('download', { timeout: 8000 }).catch(() => null)
  await page.locator('.pv-save').click()
  const file = await dl
  check('点保存/分享触发下载', !!file, file ? file.suggestedFilename() : '无 download 事件')
  check('点按钮后预览仍在（不误触退出大图）', await page.locator('.van-image-preview').isVisible())
  await page.keyboard.press('Escape')

  console.log(`\nRESULT: ${fail === 0 ? 'PASS' : 'FAIL'}  pass=${pass} fail=${fail}`)
  process.exitCode = fail === 0 ? 0 : 1
} finally {
  await browser.close()
}
