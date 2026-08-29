// 用项目自带 playwright(1.62) 跑 qa 脚本（skill 的 patchright 浏览器版本漂移，绕行）
// 用法: node scripts/run_qa_pw.mjs [scripts/xxx.mjs]
import { chromium } from 'playwright'
const mod = await import('./' + (process.argv[2] ?? 'qa_moments_pdf.mjs').replace(/^.*[\\/]/, ''))
const run = mod.default

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
const errs = []
page.on('console', (m) => m.type() === 'error' && errs.push(m.text()))
await page.goto('about:blank')
const out = await run(page)
console.log(JSON.stringify({ out, consoleErrors: errs }, null, 2))
await browser.close()
