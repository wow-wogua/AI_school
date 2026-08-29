// 补验：EP2 select 选中显示文本（selected-item）+ 学生详情条码
const BASE = 'http://localhost:5173'

export default async function run(page) {
  const out = {}
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`${BASE}/#/login`)
  await page.fill('input[placeholder="用户名"]', 'litao')
  await page.fill('input[placeholder="密码"]', 'aischool123')
  await page.click('button.van-button--primary')
  await page.waitForSelector('.stats', { timeout: 15000 })

  // 学生详情：学籍卡条码 + 校徽
  await page.goto(`${BASE}/#/student/1`)
  await page.waitForSelector('.info', { timeout: 10000 })
  out.barcode = await page.evaluate(() => ({
    bars: document.querySelectorAll('.barcode i').length,
    tag: document.querySelector('.card-tag')?.textContent,
  }))

  // 深链时间轴：select 应显示陈小华
  await page.goto(`${BASE}/#/timeline?studentId=1&termId=2`)
  await page.waitForTimeout(2500)
  out.tl = await page.evaluate(() => ({
    sel: document.querySelectorAll('.toolbar .el-select .el-select__selected-item')[1]?.textContent,
    events: document.querySelectorAll('.el-timeline-item').length,
  }))

  // 深链评价：select 应显示陈小华
  await page.goto(`${BASE}/#/evaluate?studentId=1&termId=2`)
  await page.waitForTimeout(2000)
  out.ev = await page.evaluate(() => ({
    sel: document.querySelectorAll('.toolbar .el-select .el-select__selected-item')[1]?.textContent,
    rows: document.querySelectorAll('.el-table__row').length,
  }))
  return out
}
