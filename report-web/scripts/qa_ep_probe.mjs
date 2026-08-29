// EP 页几何与对比度体检：内容块左右留白是否对称 + 扫描过浅说明文字
const BASE = 'http://localhost:5173'
const PAGES = ['scores', 'comments', 'evaluate', 'summary', 'comprehensive', 'timeline', 'honor', 'activity', 'reports']

export default async function run(page) {
  const out = {}
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto(`${BASE}/#/login`)
  await page.fill('input[placeholder="用户名"]', 'litao')
  await page.fill('input[placeholder="密码"]', 'aischool123')
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })

  for (const p of PAGES) {
    await page.goto(`${BASE}/#/${p}`)
    await page.waitForTimeout(900)
    out[p] = await page.evaluate(() => {
      const r = {}
      // 1) 主内容区与最宽卡片的水平几何
      const pg = document.querySelector('.page')
      const cards = [...document.querySelectorAll('.el-card')]
      if (pg && cards.length) {
        const widest = cards.reduce((a, b) => (b.getBoundingClientRect().width > a.getBoundingClientRect().width ? b : a))
        const P = pg.getBoundingClientRect(), C = widest.getBoundingClientRect()
        r.pageW = Math.round(P.width); r.cardW = Math.round(C.width)
        r.leftGap = Math.round(C.left - P.left); r.rightGap = Math.round(P.right - C.right)
      }
      // 2) 文字对比度扫描（白卡背景 #fff 上 <3:1 的可见文本）
      const lum = (rgb) => {
        const [rr, gg, bb] = rgb.match(/\d+/g).map(Number).map((v) => {
          v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4)
        })
        return 0.2126 * rr + 0.7152 * gg + 0.0722 * bb
      }
      const light = []
      for (const el of document.querySelectorAll('.page *')) {
        const t = [...el.childNodes].filter((n) => n.nodeType === 3).map((n) => n.textContent.trim()).join('')
        if (t.length < 4 || t.length > 90) continue
        const cs = getComputedStyle(el)
        if (cs.visibility === 'hidden' || cs.display === 'none' || +cs.opacity < 0.5) continue
        const c = cs.color
        const L = lum(c) + 0.05
        const ratio = 1.05 / L   // 白底
        if (ratio < 3.2 && !light.some((x) => x.text === t)) light.push({ text: t.slice(0, 50), color: c, ratio: +ratio.toFixed(2) })
      }
      r.lightText = light.slice(0, 6)
      return r
    })
  }
  return out
}
