// 验收⑤ 三档全流程 E2E（App 化新壳版）：手机(390x844) / 平板(768x1024) / 电脑(1440x900)
// 每档完成：登录 → 首页宫格导航 → 发起批量 → 通知页看实时进度 → 预览(PDF 200+字节校验) → 下载 → 无横向滚动
// 电脑档另做两段：班主任寄语 AI 草稿全流程 + 首登强制改密全流程（临时账号 API 建号，用后即删）
// 2026-09-08 按 App 化新壳整族重写：hash 路由（/#/）+ 首页宫格按钮 + 任务进度并入通知页（Vant）
// 运行：node e2e/verify_web.mjs（需后端 8080 + vite 5173；系统 Chrome，可用 BROWSER_CHANNEL 覆盖）
import { chromium } from 'playwright'
import fs from 'node:fs'

const BASE = process.env.BASE_URL || 'http://localhost:5173'
const API = process.env.API_URL || 'http://localhost:8080'
const H = (p) => BASE + '/#' + p
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

async function login(page, username = USER.username, password = USER.password) {
  await page.goto(H('/login'))
  await page.getByPlaceholder('用户名').fill(username)
  await page.getByPlaceholder('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL(H('/'))
}

/** goto 式导航 + 等页面唯一标记（hash 同文档跳转无 load 事件，旧页选择器会短暂存活，必须等新页挂载） */
async function navTo(page, path, marker) {
  await page.goto(H(path))
  await page.getByText(marker).first().waitFor({ state: 'visible', timeout: 10000 })
  await page.waitForTimeout(500) // 等 onMounted 的 init 接口回填（学生/报告列表等）
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
    // 1. 登录 → 首页
    await login(page)
    check(`${name} 登录成功进入首页`, true)
    check(`${name} 首页宫格≥11格`, (await page.locator('.g-item').count()) >= 11)
    await noHScroll(page, `${name} 首页`)
    await page.screenshot({ path: `${SHOTS}/${name}-1-home.png` })
    // 宫格导航：点「成长报告」进报告页（新壳导航主路径）
    await page.getByRole('button', { name: '成长报告' }).click()
    await page.waitForURL(H('/reports'))

    // 2. 发起批量（报告列表页）
    await page.waitForSelector('.el-table__row', { timeout: 15000 })
    await noHScroll(page, `${name} 报告列表页`)
    await page.screenshot({ path: `${SHOTS}/${name}-2-reports.png` })
    const capTask = page.waitForResponse(
      (r) => r.url().includes('/api/report/generate-batch') && r.request().method() === 'POST',
    ).then((r) => r.json())
    await page.getByRole('button', { name: '批量生成全班' }).click()
    const tjson = await capTask
    const taskId = tjson?.data?.taskId
    await page.waitForURL(H('/'))
    check(`${name} 发起批量成功`, !!taskId, `taskId=${taskId}`)

    // 3. 通知页看实时进度（原「批量任务」页已并入通知；点任务行选中出 Vant 进度条，轮询终态）
    await page.goto(H('/notice'))
    const taskRow = page.locator('.task.batch', { hasText: `任务 #${taskId}` })
    await taskRow.first().waitFor({ timeout: 10000 })
    await taskRow.first().click()
    await page.locator('.van-progress').first().waitFor({ timeout: 10000 })
    check(`${name} 选中任务出进度条`, true)
    let finalStatus = null
    const t0 = Date.now()
    while (Date.now() - t0 < 150_000) {
      await page.waitForTimeout(3000)
      const title = await taskRow.first().locator('.t-title').innerText().catch(() => '')
      if (/[成功|失败|部分失败]/.test(title)) {
        finalStatus = title.match(/(成功|失败|部分失败)/)[1]
        break
      }
    }
    const subs = await taskRow.first().locator('.t-sub').allInnerTexts()
    check(`${name} 批量完成`, finalStatus === '成功', `status=${finalStatus}`)
    check(`${name} 完成数=总数`, /总数\s*(\d+)\s*·\s*完成\s*\1/.test(subs.join(' ')), subs.join(' | '))
    await noHScroll(page, `${name} 通知页`)
    await page.screenshot({ path: `${SHOTS}/${name}-3-progress.png` })

    // 4. 预览（PDF 响应 200 + application/pdf + iframe blob）
    await navTo(page, '/reports', '报告列表')
    await page.waitForSelector('.el-table__row')
    const previewBtn = page.getByRole('button', { name: '预览' }).first()
    await previewBtn.waitFor({ state: 'visible', timeout: 10_000 })
    await previewBtn.click()
    await page.waitForURL((u) => /\/#\/reports\/\d+\/preview/.test(u.toString()))
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
    await navTo(page, '/comments', '班主任寄语') // 前页 /reports 也有 el-select，必须等新页标记
    await noHScroll(page, `${name} 寄语页`)
    if (name === 'desktop') {
      await page.locator('.el-select').nth(1).click() // 学生下拉
      await page.locator('.el-select-dropdown__item:visible').first().click()
      await page.waitForFunction(() => {
        const t = document.querySelector('textarea')
        return t && t.value.length > 20 // load() 回填已有寄语
      }, { timeout: 15_000 })
      // 任务化：点击提交 → 轮询 → 完成后回填（捕获任务详情响应比对，模板输出确定性重复也不影响）
      let taskDetail = null
      const capDetail = page.waitForResponse(
        (r) => /\/api\/ai\/tasks\/\d+$/.test(r.url()) && r.status() === 200,
      ).then(async (r) => (taskDetail = (await r.json()).data))
      await page.getByRole('button', { name: 'AI 生成草稿' }).click()
      await capDetail
      await page.waitForFunction(
        (d) => {
          const t = document.querySelector('textarea')
          return t && d?.result?.draft && t.value === d.result.draft
        },
        taskDetail,
        { timeout: 10_000 },
      )
      const draft = await page.locator('textarea').inputValue()
      check('desktop AI 草稿生成并回填', draft === taskDetail.result.draft && draft.includes('同学'), draft.slice(0, 30))
      await page.getByRole('button', { name: '确认生效' }).click()
      await page.waitForSelector('.el-message--success', { timeout: 10_000 })
      check('desktop 寄语确认生效', true)
      await page.screenshot({ path: `${SHOTS}/${name}-5-comment.png` })
    }
  } catch (e) {
    check(`${name} 全流程`, false, String(e).split('\n').slice(0, 3).join(' | '))
    await page.screenshot({ path: `${SHOTS}/${name}-error.png` }).catch(() => {})
  } finally {
    await ctx.close()
  }
}

// ── 首登强制改密全流程（电脑档加练；临时账号走 admin API 建号/删号，失败也兜底清理） ──
async function mcpFlow(browser) {
  console.log('\n===== 首登强制改密（临时账号 e2e_mcp） =====')
  const loginApi = async (username, password) =>
    (await fetch(API + '/api/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    })).json()
  const admin = (await loginApi('admin', 'admin123'))?.data
  const headers = { 'Content-Type': 'application/json', Authorization: 'Bearer ' + admin.token }
  // 预清理：上次运行若 uid 未解析到，finally 删号被跳过会残留账号
  const findIds = async () =>
    ((await (await fetch(API + '/api/admin/user/list?keyword=e2e_mcp', { headers })).json())?.data?.records || [])
      .map((u) => u.id)
  for (const id of await findIds()) await fetch(`${API}/api/admin/user/${id}`, { method: 'DELETE', headers }).catch(() => {})
  const created = await (await fetch(API + '/api/admin/user', {
    method: 'POST', headers,
    body: JSON.stringify({ username: 'e2e_mcp', password: 'Passw0rd9', realName: 'E2E临时账号', role: 'TEACHER' }),
  })).json()
  const uid = created?.data?.userId ?? (await findIds())[0] // 建号返回 userId；异常时兜底查列表
  try {
    check('临时账号建号 code=0（初始密码置强制改密）', created?.code === 0 && !!uid, `uid=${uid}`)
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
    const page = await ctx.newPage()
    try {
      await page.goto(H('/login'))
      await page.getByPlaceholder('用户名').fill('e2e_mcp')
      await page.getByPlaceholder('密码').fill('Passw0rd9')
      await page.getByRole('button', { name: '登录' }).click()
      await page.waitForURL(H('/change-password'))
      check('登录即拦到改密页', true)
      // 未改密前访问业务页被路由守卫弹回
      await page.goto(H('/scores'))
      await page.waitForTimeout(800)
      check('未改密访问业务被弹回改密页', page.url().includes('#/change-password'), page.url())
      await page.getByPlaceholder('当前密码（管理员下发）').fill('Passw0rd9')
      await page.getByPlaceholder('新密码（至少 8 位）').fill('NewPassw0rd9')
      await page.getByPlaceholder('再输入一遍新密码').fill('NewPassw0rd9')
      await page.getByRole('button', { name: '保存并进入系统' }).click()
      await page.waitForURL(H('/'), { timeout: 10_000 })
      check('改密后进入首页', true)
      await page.goto(H('/scores'))
      // 无班无课教师 rows 恒空：等 loaded 后的空态文案（能出现=接口带新 JWT 调通）
      await page.getByText('选择考试/班级/学科后加载成绩单').waitFor({ timeout: 15_000 })
      check('改密后业务页可正常访问', page.url().includes('#/scores'))
      await page.screenshot({ path: `${SHOTS}/mcp-after-change.png` })
    } finally {
      await ctx.close()
    }
  } catch (e) {
    check('强制改密全流程', false, String(e).split('\n')[0])
  } finally {
    if (uid) await fetch(`${API}/api/admin/user/${uid}`, { method: 'DELETE', headers }).catch(() => {})
  }
}

// 用系统 Chrome（channel），免下载 190MB playwright chromium
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL || 'chrome' })
await fullFlow(browser, { name: 'desktop', width: 1440, height: 900, mobile: false })
await fullFlow(browser, { name: 'tablet', width: 768, height: 1024, mobile: false })
await fullFlow(browser, { name: 'mobile', width: 390, height: 844, mobile: true })
await mcpFlow(browser)
await browser.close()

console.log(`\nRESULT: ${fail === 0 ? 'PASS' : 'FAIL'}  pass=${pass} fail=${fail}`)
process.exit(fail === 0 ? 0 : 1)
