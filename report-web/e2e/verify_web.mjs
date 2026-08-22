// 验收⑤ 三档全流程 E2E：手机(390x844) / 平板(768x1024) / 电脑(1440x900)
// 每档完成：登录 → 发起批量 → 看实时进度 → 预览(PDF 200+字节校验) → 下载(文件落盘校验) → 无横向滚动
// 运行：PLAYWRIGHT_BROWSERS_PATH=D:/srp_project/AI_school/tools/pw-browsers node e2e/verify_web.mjs
import { chromium } from 'playwright'
import fs from 'node:fs'

const BASE = process.env.BASE_URL || 'http://localhost:5173'
const USER = { username: 'zhaolaoshi', password: 'aischool123' } // 初一(2)班班主任（2 人班，批量快）
const SHOTS = 'e2e/shots'
fs.mkdirSync(SHOTS, { recursive: true })

let pass = 0, fail = 0
const check = (name, cond, detail = '') => {
  console.log(`  ${cond ? 'PASS' : 'FAIL'} ${name}  ${detail}`)
  cond ? pass++ : fail++
}

async function noHScroll(page, tag) {
  const w = await page.evaluate(() => ({
    sw: document.documentElement.scrollWidth,
    iw: window.innerWidth,
  }))
  check(`${tag} 无横向滚动`, w.sw <= w.iw + 1, `scrollWidth=${w.sw} innerWidth=${w.iw}`)
}

async function fullFlow(browser, { name, width, height, mobile }) {
  console.log(`\n===== ${name} ${width}x${height} =====`)
  const ctx = await browser.newContext({ viewport: { width, height }, isMobile: mobile, hasTouch: mobile })
  const page = await ctx.newPage()
  const pdfResponses = []
  page.on('response', async (r) => {
    if (r.url().includes('/api/report/file/')) {
      pdfResponses.push({ status: r.status(), type: r.headers()['content-type'] })
    }
  })
  try {
    // 1. 登录
    await page.goto(BASE + '/login')
    await page.getByPlaceholder('用户名').fill(USER.username)
    await page.getByPlaceholder('密码').fill(USER.password)
    await page.screenshot({ path: `${SHOTS}/${name}-1-login.png` })
    await noHScroll(page, `${name} 登录页`)
    await page.getByRole('button', { name: '登录' }).click()
    await page.waitForURL(BASE + '/')
    check(`${name} 登录成功进入任务页`, true)

    // 2. 发起批量（报告列表页）
    await page.getByRole('link', { name: '报告列表' }).click()
    await page.waitForURL(BASE + '/reports')
    await page.waitForSelector('.el-table__row', { timeout: 15000 })
    await noHScroll(page, `${name} 报告列表页`)
    await page.screenshot({ path: `${SHOTS}/${name}-2-reports.png` })
    await page.getByRole('button', { name: '批量生成全班' }).click()
    await page.waitForURL(BASE + '/')
    check(`${name} 发起批量成功`, true)

    // 3. 看实时进度（进度条推进，is-success 时文本是对勾图标，读 aria-valuenow）
    await page.waitForSelector('.el-progress', { timeout: 15000 })
    const readPct = () =>
      page.locator('.el-progress').first().getAttribute('aria-valuenow').then((v) => parseInt(v || '0'))
    const p1 = await readPct()
    let pctSeen = p1, finalStatus = null
    const t0 = Date.now()
    while (Date.now() - t0 < 150_000) {
      await page.waitForTimeout(3000)
      const pct = await readPct()
      pctSeen = Math.max(pctSeen, pct)
      const header = await page.locator('.el-card__header').first().innerText()
      if (/[成功|失败|部分失败]/.test(header)) {
        finalStatus = header.match(/(成功|失败|部分失败)/)[1]
        break
      }
    }
    check(`${name} 进度实时推进（≥99%）`, pctSeen >= 99, `max=${pctSeen}%`)
    check(`${name} 批量完成`, finalStatus === '成功', `status=${finalStatus}`)
    await page.screenshot({ path: `${SHOTS}/${name}-3-progress.png` })

    // 4. 预览（PDF 响应 200 + application/pdf + 落盘字节 >100k）
    await page.getByRole('link', { name: '报告列表' }).click()
    await page.waitForURL(BASE + '/reports')
    await page.waitForSelector('.el-table__row')
    const previewBtn = page.getByRole('button', { name: '预览' }).first()
    await previewBtn.waitFor({ state: 'visible', timeout: 10_000 })
    await previewBtn.click()
    await page.waitForURL(/\/reports\/\d+\/preview/)
    await page.waitForFunction(() => document.querySelector('iframe[src^="blob:"]') !== null, { timeout: 30_000 })
    await page.waitForTimeout(1500)
    const pdfOk = pdfResponses.some((r) => r.status === 200 && /pdf/.test(r.type))
    check(`${name} 预览加载 PDF（200 application/pdf）`, pdfOk, JSON.stringify(pdfResponses.slice(-1)))
    await noHScroll(page, `${name} 预览页`)
    await page.screenshot({ path: `${SHOTS}/${name}-4-preview.png` })

    // 5. 下载（Playwright download 事件 + 落盘校验 %PDF 头）
    await page.goBack()
    await page.waitForSelector('.el-table__row')
    const dlPromise = page.waitForEvent('download', { timeout: 30_000 })
    await page.getByRole('button', { name: '下载' }).first().click()
    const dl = await dlPromise
    const path = `${SHOTS}/${name}-download.pdf`
    await dl.saveAs(path)
    const buf = fs.readFileSync(path)
    check(`${name} 下载 PDF 完整`, buf.length > 100_000 && buf.subarray(0, 5).toString() === '%PDF-', `${buf.length}B`)

    // 6. 班主任寄语 AI 草稿（电脑档做全流程，其余档验证页面可用）
    await page.getByRole('link', { name: '班主任寄语' }).click()
    await page.waitForURL(BASE + '/comments')
    await page.waitForSelector('.el-select')
    await noHScroll(page, `${name} 寄语页`)
    if (name === 'desktop') {
      await page.locator('.el-select').nth(1).click() // 学生下拉
      await page.locator('.el-select-dropdown__item:visible').first().click()
      await page.waitForFunction(() => {
        const t = document.querySelector('textarea')
        return t && t.value.length > 20 // load() 回填已有寄语
      }, { timeout: 15_000 })
      let draftResp = null
      const capDraft = page.waitForResponse(
        (r) => r.url().includes('/api/ai/comment-draft') && r.status() === 200,
      ).then(async (r) => (draftResp = (await r.json()).data))
      await page.getByRole('button', { name: 'AI 生成草稿' }).click()
      await capDraft
      // 草稿回填编辑框（与接口返回一致）
      await page.waitForFunction(
        (d) => document.querySelector('textarea')?.value === d?.draft,
        draftResp,
        { timeout: 10_000 },
      )
      const draft = await page.locator('textarea').inputValue()
      check('desktop AI 草稿生成并回填', draft === draftResp.draft && draft.includes('同学'), draft.slice(0, 30))
      await page.getByRole('button', { name: '确认生效' }).click()
      await page.waitForSelector('.el-message--success', { timeout: 10_000 })
      check('desktop 寄语确认生效', true)
      await page.screenshot({ path: `${SHOTS}/${name}-5-comment.png` })
    }
  } catch (e) {
    check(`${name} 全流程`, false, String(e).slice(0, 200))
    await page.screenshot({ path: `${SHOTS}/${name}-error.png` }).catch(() => {})
  } finally {
    await ctx.close()
  }
}

// 用系统 Chrome（channel），免下载 190MB playwright chromium
const browser = await chromium.launch({ channel: 'chrome' })
await fullFlow(browser, { name: 'desktop', width: 1440, height: 900, mobile: false })
await fullFlow(browser, { name: 'tablet', width: 768, height: 1024, mobile: false })
await fullFlow(browser, { name: 'mobile', width: 390, height: 844, mobile: true })
await browser.close()

console.log(`\nRESULT: ${fail === 0 ? 'PASS' : 'FAIL'}  pass=${pass} fail=${fail}`)
process.exit(fail === 0 ? 0 : 1)
