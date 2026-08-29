// 微光信箱 UI 全流程：拍照页交互 → 提交 → 成功激励态 → 班级轮播/学生照片墙/记录流照片卡
import { mkdirSync } from 'node:fs'

const SHOTS = 'D:/srp_project/AI_school/report-web/shots'
mkdirSync(SHOTS, { recursive: true })
const BASE = 'http://localhost:5173'

export default async function run(page) {
  const out = {}
  await page.setViewportSize({ width: 390, height: 844 })

  // 登录
  await page.goto(`${BASE}/#/login`)
  await page.fill('input[placeholder="用户名"]', 'litao')
  await page.fill('input[placeholder="密码"]', 'aischool123')
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })

  // 首页：宫格含微光信箱；快捷菜单拍照记录排第一
  out.gridHasMoment = await page.evaluate(() =>
    [...document.querySelectorAll('.g-item')].filter((el) => el.textContent.includes('微光信箱')).length)
  await page.click('.fab-tab')
  await page.waitForSelector('.van-action-sheet__item', { timeout: 5000 })
  out.quickFirst = await page.evaluate(() =>
    document.querySelector('.van-action-sheet__item')?.textContent?.trim())
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)

  // 拍照页：空态 → 选照片 → 标签/学生 → 提交
  await page.goto(`${BASE}/#/moment/new`)
  await page.waitForSelector('.photo-card.empty', { timeout: 10000 })
  await page.waitForSelector('.tag', { timeout: 8000 })
  out.tags = await page.locator('.tag').count()
  out.stuCount = await page.locator('.stu').count()
  out.disabledAtStart = await page.locator('.submit').isDisabled()

  await page.setInputFiles('input[type=file]', 'D:/srp_project/AI_school/report-web/public/campus-bg.jpg')
  await page.waitForSelector('.preview', { timeout: 5000 })
  await page.click('.tag:nth-child(2)') // 场景标签（第2个）
  await page.click('.stu >> nth=0')
  await page.click('.stu >> nth=1')
  out.pickedOn = await page.locator('.stu.on').count()
  out.enabledBeforeSubmit = !(await page.locator('.submit').isDisabled())
  await page.screenshot({ path: `${SHOTS}/m3-capture-filled.png` })

  await page.click('.submit')
  await page.waitForSelector('.done-card', { timeout: 15000 })
  out.doneText = (await page.locator('.done-card p').first().textContent())?.trim()
  await page.screenshot({ path: `${SHOTS}/m3-capture-done.png` })

  // 班级页：本周微光轮播（含刚提交的一条）
  await page.goto(`${BASE}/#/class`)
  await page.waitForSelector('.mo-card', { timeout: 10000 })
  await page.waitForTimeout(1200) // 等照片流拉取
  out.classMoments = await page.locator('.mo-card').count()
  out.classPhotosLoaded = await page.evaluate(() =>
    [...document.querySelectorAll('.mo-card img')].filter((i) => i.complete && i.naturalWidth > 0).length)
  await page.screenshot({ path: `${SHOTS}/m3-class-moments.png` })

  // 学生详情：TA的闪光时刻（陈小华 id=1，API 建的 2 条 + UI 提交的 1 条）
  await page.goto(`${BASE}/#/student/1`)
  await page.waitForSelector('.mo-item', { timeout: 10000 })
  await page.waitForTimeout(1200)
  out.studentMoments = await page.locator('.mo-item').count()
  out.studentPhotosLoaded = await page.evaluate(() =>
    [...document.querySelectorAll('.mo-item img')].filter((i) => i.complete && i.naturalWidth > 0).length)
  await page.screenshot({ path: `${SHOTS}/m3-student-moments.png` })

  // 成长记录流：微光筛选 + 照片卡
  await page.goto(`${BASE}/#/feed`)
  await page.waitForSelector('.card', { timeout: 10000 })
  await page.click('.chip >> nth=1') // 「微光」
  await page.waitForTimeout(1500)
  out.feedMomentCards = await page.evaluate(() =>
    [...document.querySelectorAll('.card')].filter((c) => c.querySelector('.c-photo')).length)
  out.feedPhotosLoaded = await page.evaluate(() =>
    [...document.querySelectorAll('.c-photo img')].filter((i) => i.complete && i.naturalWidth > 0).length)
  out.feedTitle = (await page.locator('.card .c-title').first().textContent())?.trim()
  await page.screenshot({ path: `${SHOTS}/m3-feed-moment.png` })

  return out
}
