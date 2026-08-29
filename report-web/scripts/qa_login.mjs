// 登录页背景清晰度复验：blur 3px 后校门应可辨认
const SHOTS = 'D:/srp_project/AI_school/report-web/shots'
const BASE = 'http://localhost:5173'

export default async function run(page) {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`${BASE}/#/login`)
  await page.waitForSelector('input[placeholder="用户名"]', { timeout: 15000 })
  await page.waitForTimeout(600) // 等背景图加载
  const bg = await page.evaluate(() => {
    const cs = getComputedStyle(document.querySelector('.login-bg'))
    return { img: cs.backgroundImage.includes('campus-bg'), filter: cs.filter }
  })
  await page.screenshot({ path: `${SHOTS}/f3-login-m.png` })
  return bg
}
