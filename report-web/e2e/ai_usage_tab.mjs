// D2 冒烟：管理页「AI 用量」Tab 渲染真实 tokens 数据
export default async function run(page) {
  await page.goto('http://localhost:5173/login')
  await page.locator('input').nth(0).fill('admin')
  await page.locator('input[type=password]').fill('admin123')
  await page.getByRole('button').click()
  await page.waitForURL('**/', { timeout: 10000 })
  await page.goto('http://localhost:5173/admin')
  await page.getByRole('tab', { name: 'AI 用量' }).click()
  await page.waitForTimeout(1200)
  const rows = await page.locator('.el-table__body tr').allInnerTexts()
  await page.screenshot({ path: 'e2e/shots/d2_ai_usage_tab.png', fullPage: false })
  return { rows: rows.slice(0, 4), hasTokens: rows.join('\n').includes('1,498') }
}
