// 找出桌面端底部蓝条的来源元素
export default async function run(page, ui) {
  await page.setViewportSize({ width: 1280, height: 800 })
  await page.goto('http://localhost:5173/#/login')
  await page.waitForSelector('input[placeholder="用户名"]', { timeout: 15000 })
  await page.fill('input[placeholder="用户名"]', 'litao')
  await page.fill('input[placeholder="密码"]', 'aischool123')
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })
  await page.waitForTimeout(600)
  return await page.evaluate(() => {
    const n = document.evaluate('//*[contains(text(),"EST. 1999")]', document, null, 9, null).singleNodeValue
    if (!n) return { found: false }
    const el = n
    const path = []
    let cur = el
    while (cur && cur !== document.body) { path.unshift(cur.tagName + '.' + (cur.className?.toString().slice(0, 40) ?? '')); cur = cur.parentElement }
    const cs = getComputedStyle(el.parentElement ?? el)
    return {
      found: true, path,
      parentPos: cs.position, parentBg: cs.backgroundImage.slice(0, 80),
      rect: JSON.parse(JSON.stringify((el.parentElement ?? el).getBoundingClientRect())),
      url: location.href,
    }
  })
}
