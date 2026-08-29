// 验证批次：①叠卡z-index（统计数字可见）②宫格彩色 ③hero照片带 ④登录页新背景
import { mkdirSync } from 'node:fs'

const SHOTS = 'D:/srp_project/AI_school/report-web/shots'
mkdirSync(SHOTS, { recursive: true })

async function login(page, w, h) {
  await page.setViewportSize({ width: w, height: h })
  await page.goto('http://localhost:5173/#/login')
  await page.waitForSelector('input[placeholder="用户名"]', { timeout: 15000 })
  await page.fill('input[placeholder="用户名"]', 'litao')
  await page.fill('input[placeholder="密码"]', 'aischool123')
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })
  await page.waitForTimeout(700)
}

export default async function run(page) {
  const out = {}

  // 手机视口（先截登录页再登录）
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('http://localhost:5173/#/login')
  await page.waitForSelector('input[placeholder="用户名"]', { timeout: 15000 })
  await page.waitForTimeout(400)
  await page.screenshot({ path: `${SHOTS}/f-login-m.png` })
  out.loginBg = await page.evaluate(() => (document.querySelector('.login-bg') ? getComputedStyle(document.querySelector('.login-bg')).backgroundImage.includes('login-campus') : null))
  await page.fill('input[placeholder="用户名"]', 'litao')
  await page.fill('input[placeholder="密码"]', 'aischool123')
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })
  await page.waitForTimeout(700)
  await page.screenshot({ path: `${SHOTS}/f-home-m.png` })
  out.mHome = await page.evaluate(() => {
    const hero = document.querySelector('.app-hero .hero-photo')
    const r = hero?.getBoundingClientRect()
    const stat = document.querySelector('.stat b')
    const sr = stat?.getBoundingClientRect()
    const heroBox = document.querySelector('.app-hero').getBoundingClientRect()
    const icons = [...document.querySelectorAll('.g-icon')].map((el) => getComputedStyle(el).backgroundColor)
    return {
      photo: r ? { y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height), loaded: hero.complete && hero.naturalWidth > 0 } : null,
      statVisible: sr ? sr.y > heroBox.y + heroBox.height - 45 : null, // 数字中心应在 hero 下缘之上（卡片上浮区可见）
      statText: stat?.textContent,
      iconColors: [...new Set(icons)],
    }
  })
  await page.goto('http://localhost:5173/#/student/1')
  await page.waitForSelector('.app-card.grid', { timeout: 15000 })
  await page.waitForTimeout(600)
  await page.screenshot({ path: `${SHOTS}/f-stu-m.png` })
  out.mStu = await page.evaluate(() => ({
    photo: !!document.querySelector('.hero-photo'),
    icons: [...new Set([...document.querySelectorAll('.g-icon')].map((el) => getComputedStyle(el).backgroundColor))],
  }))

  // 桌面视口
  await login(page, 1280, 800)
  await page.screenshot({ path: `${SHOTS}/f-home-d.png` })
  out.dHome = await page.evaluate(() => {
    const heroBox = document.querySelector('.app-hero').getBoundingClientRect()
    const stat = document.querySelector('.stat b')
    const card = document.querySelector('.app-card.overlap')
    const cardTop = card.getBoundingClientRect().top
    const cs = getComputedStyle(card)
    return {
      heroBottom: Math.round(heroBox.bottom),
      cardTop: Math.round(cardTop),
      cardPos: cs.position, cardZ: cs.zIndex,
      statOnCard: stat.getBoundingClientRect().top >= cardTop,
    }
  })
  await page.goto('http://localhost:5173/#/mine')
  await page.waitForTimeout(700)
  await page.screenshot({ path: `${SHOTS}/f-mine-d.png` })
  out.dMine = await page.evaluate(() => {
    const card = document.querySelector('.app-card.overlap')
    const cell = card.querySelector('.van-cell')
    return { cellTop: Math.round(cell.getBoundingClientRect().top), cardTop: Math.round(card.getBoundingClientRect().top) }
  })

  out.err = null
  return out
}
