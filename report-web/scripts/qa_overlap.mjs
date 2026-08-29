// 桌面端(1280x800)重叠诊断：我的页+首页，取元素矩形与底部/中部取样点的最顶层元素
import { mkdirSync } from 'node:fs'

const SHOTS = 'D:/srp_project/AI_school/report-web/shots'
mkdirSync(SHOTS, { recursive: true })

const probe = () => {
  const out = { url: location.hash, vw: innerWidth, vh: innerHeight, rects: {}, hits: [] }
  for (const sel of ['.app-page', '.app-hero', '.app-card.overlap', '.cells', '.app-shell', '.app-main', '.app-tabbar', '.van-tabbar', '.app-footer', '.nav']) {
    document.querySelectorAll(sel).forEach((el, i) => {
      const r = el.getBoundingClientRect()
      const cs = getComputedStyle(el)
      out.rects[sel + ':' + i] = {
        x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height),
        bg: cs.backgroundColor, pos: cs.position, z: cs.zIndex, maxW: cs.maxWidth,
      }
    })
  }
  // 取样：页面高度上每隔 80px 取中线的最顶层元素
  for (let y = 40; y < innerHeight; y += 80) {
    const els = document.elementsFromPoint(innerWidth / 2, y)
    const top = els.find(e => e !== document.documentElement && e !== document.body && !e.classList.contains('app-shell') && !e.classList.contains('app-main'))
    if (top) out.hits.push({ y, tag: top.tagName, cls: (top.className?.toString() ?? '').slice(0, 50), text: (top.textContent ?? '').trim().slice(0, 24) })
  }
  return out
}

export default async function run(page) {
  await page.setViewportSize({ width: 1280, height: 800 })
  await page.goto('http://localhost:5173/#/login')
  await page.waitForSelector('input[placeholder="用户名"]', { timeout: 15000 })
  await page.fill('input[placeholder="用户名"]', 'litao')
  await page.fill('input[placeholder="密码"]', 'aischool123')
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })
  await page.waitForTimeout(600)

  const home = await page.evaluate(probe)
  await page.screenshot({ path: `${SHOTS}/o-home.png` })

  await page.goto('http://localhost:5173/#/mine')
  await page.waitForTimeout(800)
  const mine = await page.evaluate(probe)
  await page.screenshot({ path: `${SHOTS}/o-mine.png` })

  return { home, mine }
}
