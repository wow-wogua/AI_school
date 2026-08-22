// M7 E2E：成绩 / 日常评价 / 成长总结 / 综合素质 / 系统管理 / 报告列表年级视角
// 桌面(1440x900) + 手机(390x844)；三角色 admin / wanglaoshi（初一(2)班语文任课）/ zhaolaoshi（初一(2)班班主任）
// 全部写操作走 class2；脚本自带 mysqldump 快照信封，结束恢复（评价会平移学生1 gradeAvg，必须还原）
// 运行：node e2e/verify_m7_web.mjs（需后端 8080 + vite 5173；系统 Chrome + docker MySQL）
import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'

const BASE = process.env.BASE_URL || 'http://localhost:5173'
const SHOTS = 'e2e/shots'
const SNAP = 'e2e/_m7web_snapshot.sql'
const TABLES = ('t_user t_teach t_grade t_class t_student t_term t_report_template t_indicator '
  + 't_exam t_exam_subject t_score t_evaluation t_grid_stat_term t_grid_stat_week t_coin_week '
  + 't_coin_income t_coin_account t_class_grid_avg t_grade_grid_avg t_comprehensive '
  + 't_activity t_activity_signup t_report_task t_report').split(' ')
fs.mkdirSync(SHOTS, { recursive: true })

let pass = 0, fail = 0
const check = (name, cond, detail = '') => {
  console.log(`  ${cond ? 'PASS' : 'FAIL'} ${name}  ${detail}`)
  cond ? pass++ : fail++
}

function dump() {
  const out = fs.openSync(SNAP, 'w')
  execFileSync('docker', ['exec', 'aischool-mysql', 'mysqldump',
    '-uroot', '-paischool123', '--default-character-set=utf8mb4', 'ai_school', ...TABLES],
    { stdio: ['ignore', out, 'inherit'] })
  fs.closeSync(out)
}

function restore() {
  const inp = fs.openSync(SNAP, 'r')
  execFileSync('docker', ['exec', '-i', 'aischool-mysql', 'mysql',
    '--default-character-set=utf8mb4', '-uroot', '-paischool123', 'ai_school'],
    { stdio: [inp, 'inherit', 'inherit'] })
  fs.closeSync(inp)
}

async function noHScroll(page, tag) {
  const w = await page.evaluate(() => ({
    sw: document.documentElement.scrollWidth,
    iw: window.innerWidth,
  }))
  check(`${tag} 无横向滚动`, w.sw <= w.iw + 1, `scrollWidth=${w.sw} innerWidth=${w.iw}`)
}

async function login(page, username, password) {
  await page.goto(BASE + '/login')
  await page.getByPlaceholder('用户名').fill(username)
  await page.getByPlaceholder('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL(BASE + '/')
}

/** 展开第 n 个 el-select，点可见选项（含文本匹配可选）；等待选项渲染完成 */
async function pickSelect(page, nth, text) {
  await page.locator('.el-select').nth(nth).click()
  const items = page.locator('.el-select-dropdown:visible .el-select-dropdown__item')
  const target = text === undefined ? items.first() : items.filter({ hasText: text }).first()
  await target.waitFor({ state: 'visible', timeout: 10000 })
  await target.click()
}

async function countSelectOptions(page, nth) {
  await page.locator('.el-select').nth(nth).click()
  const items = page.locator('.el-select-dropdown:visible .el-select-dropdown__item')
  await items.first().waitFor({ state: 'visible', timeout: 10000 })
  const n = await items.count()
  await page.keyboard.press('Escape')
  return n
}

// ── admin：新建考试（M7-E2E）→ 系统管理六页 → 报告列表年级视角 ──
async function adminFlow(ctx, name, examName) {
  const page = await ctx.newPage()
  try {
    await login(page, 'admin', 'admin123')
    check(`${name} admin 见系统管理入口`, (await page.getByRole('link', { name: '系统管理' }).count()) === 1)

    // /scores：新建考试（写路径走 UI，日期 2026-06-13 早于期末，不动 latestExam）
    await page.getByRole('link', { name: '成绩管理' }).click()
    await page.waitForURL(BASE + '/scores')
    await page.waitForSelector('.el-table__row', { timeout: 15000 }) // 成绩单加载=学科上下文就绪
    const subjCount = await countSelectOptions(page, 2) // 0=考试 1=班级 2=学科（exam1 全科）
    check(`${name} admin 学科全科可见`, subjCount >= 2, `n=${subjCount}`)
    await page.getByRole('button', { name: '新建考试' }).click()
    const dlg = page.locator('.el-dialog').last()
    await dlg.waitFor({ state: 'visible' })
    await dlg.getByPlaceholder('如期中考试').fill(examName)
    await dlg.locator('.el-form-item', { hasText: '考试日期' }).locator('input').fill('2026-06-13')
    await page.keyboard.press('Enter') // Enter 提交日期（Escape 会取消编辑导致 examDate 空）
    await page.waitForTimeout(400)
    await dlg.locator('.el-select').nth(1).click() // 0=学期(默认) 1=第一科
    const yuwenOpt = page.locator('.el-select-dropdown:visible .el-select-dropdown__item')
      .filter({ hasText: '语文' }).first()
    await yuwenOpt.waitFor({ state: 'visible', timeout: 10000 })
    await yuwenOpt.click()
    await dlg.getByRole('button', { name: '创建' }).click()
    await page.waitForTimeout(1500)
    check(`${name} 建考试成功`, (await page.getByText('考试已创建').count()) > 0)

    // /admin：六页签 + 模板锁
    await page.getByRole('link', { name: '系统管理' }).click()
    await page.waitForURL(BASE + '/admin')
    await page.waitForSelector('.el-table__row', { timeout: 10000 })
    check(`${name} admin 教师页加载`, (await page.locator('.el-table__row').count()) > 0)
    const tabs = ['年级与班级', '学生', '学期', '九格指标', '报告模板']
    for (const t of tabs) await page.getByRole('tab', { name: t }).click()
    await page.getByText('锁定').first().waitFor({ timeout: 10000 }) // 模板页启用行渲染完成
    check(`${name} 模板页含锁定标记`, (await page.getByText('锁定').count()) > 0)
    await noHScroll(page, `${name} admin页`)
    await page.screenshot({ path: `${SHOTS}/m7-${name}-1-admin.png` })

    // /reports：班级/全年级切换
    await page.getByRole('link', { name: '报告列表' }).click()
    await page.waitForURL(BASE + '/reports')
    await page.locator('.el-radio-button', { hasText: '全年级' }).waitFor({ timeout: 10000 })
    await page.locator('.el-radio-button', { hasText: '全年级' }).click()
    check(`${name} 年级视角出批量按钮`, (await page.getByRole('button', { name: '批量生成全年级' }).count()) === 1)
    await noHScroll(page, `${name} 报告页`)
    await page.screenshot({ path: `${SHOTS}/m7-${name}-2-reports-grade.png` })
  } finally {
    await page.close()
  }
}

// ── wanglaoshi：任课录入成绩 + 日常评价写穿 ──
async function wangFlow(ctx, name, examName) {
  const page = await ctx.newPage()
  try {
    await login(page, 'wanglaoshi', 'aischool123')
    check(`${name} 任课不见系统管理`, (await page.getByRole('link', { name: '系统管理' }).count()) === 0)

    // /scores：只见语文；用 admin 建的 M7-E2E 考试录一笔
    await page.getByRole('link', { name: '成绩管理' }).click()
    await page.waitForURL(BASE + '/scores')
    await page.waitForSelector('.el-table__row', { timeout: 15000 }) // 成绩单就绪
    check(`${name} 任课本学科可编辑`, (await page.locator('.el-table__row .el-input-number').count()) > 0)
    // 切数学（非任课学科）→ 只读态
    await pickSelect(page, 2, '数学')
    await page.waitForTimeout(800)
    check(`${name} 非任课学科只读`, (await page.getByText('非本班本学科任课教师，只读').count()) > 0)
    await pickSelect(page, 2, '语文') // 切回可写学科
    await page.waitForTimeout(800)
    await pickSelect(page, 0, examName)
    await page.waitForSelector('.el-table__row', { timeout: 10000 })
    const firstInput = page.locator('.el-table__row').first().locator('input')
    await firstInput.fill('95')
    await page.getByRole('button', { name: '批量保存' }).click()
    await page.waitForTimeout(1500)
    const scoreVal = await page.locator('.el-table__row').first().locator('input').inputValue()
    const rowText = await page.locator('.el-table__row').first().innerText()
    check(`${name} 录入后出名次`, scoreVal === '95' && /1/.test(rowText),
          `score=${scoreVal} ${rowText.replace(/\s+/g, ' ')}`)
    await noHScroll(page, `${name} 成绩页`)
    await page.screenshot({ path: `${SHOTS}/m7-${name}-3-score.png` })

    // /evaluate：选第一个学生 +2 提交
    await page.getByRole('link', { name: '日常评价' }).click()
    await page.waitForURL(BASE + '/evaluate')
    await page.waitForSelector('.el-select', { timeout: 10000 })
    await pickSelect(page, 1) // 0=班级(默认) 1=学生
    await page.waitForSelector('.el-radio-button', { timeout: 8000 })
    await page.locator('.el-radio-button', { hasText: '+2' }).click()
    await page.getByPlaceholder('如：课堂发言精彩 / 作业未完成').fill('E2E课堂发言精彩')
    await page.getByRole('button', { name: '提交评价' }).click()
    await page.waitForTimeout(1500)
    check(`${name} 评价提交成功`, (await page.getByText('已记录（第').count()) > 0)
    check(`${name} 历史表含新评价`, (await page.getByText('E2E课堂发言精彩').count()) > 0)
    await noHScroll(page, `${name} 评价页`)
    await page.screenshot({ path: `${SHOTS}/m7-${name}-4-evaluate.png` })
  } finally {
    await page.close()
  }
}

// ── zhaolaoshi：综评五维保存 + 成长总结 ──
async function zhaoFlow(ctx, name) {
  const page = await ctx.newPage()
  try {
    await login(page, 'zhaolaoshi', 'aischool123')

    // /comprehensive
    await page.getByRole('link', { name: '综合素质' }).click()
    await page.waitForURL(BASE + '/comprehensive')
    await page.waitForSelector('.el-select', { timeout: 10000 })
    await pickSelect(page, 1) // 学生
    await page.waitForTimeout(600) // 等学生下拉关闭动画走完，避免旧 popper 干扰 :visible 匹配
    await page.waitForSelector('.el-form-item', { timeout: 8000 })
    const levels = ['A', 'A', 'B', 'B', 'C']
    for (let i = 0; i < 5; i++) {
      // 表单内五个维度 select（toolbar 3 个之后的第 i 个）
      await page.locator('.el-card .el-select').nth(i).click()
      await page.waitForTimeout(400)
      await page.locator('.el-select-dropdown:visible .el-select-dropdown__item')
        .filter({ hasText: new RegExp(`^${levels[i]}$`) }).first().click()
      await page.waitForTimeout(200)
    }
    check(`${name} final 预览=并列取高A`, (await page.locator('.el-tag', { hasText: 'A' }).count()) > 0)
    await page.getByRole('button', { name: '保存' }).click()
    await page.waitForTimeout(1500)
    check(`${name} 综评保存成功`, (await page.getByText('已保存，综合等级 A').count()) > 0)
    await noHScroll(page, `${name} 综评页`)
    await page.screenshot({ path: `${SHOTS}/m7-${name}-5-comprehensive.png` })

    // /summary
    await page.getByRole('link', { name: '成长总结' }).click()
    await page.waitForURL(BASE + '/summary')
    await page.waitForSelector('.el-select', { timeout: 10000 })
    await pickSelect(page, 1) // 学生
    await page.getByRole('button', { name: 'AI 分析该学生' }).click()
    await page.locator('.el-card').first().waitFor({ timeout: 20000 })
    const titles = await page.locator('.el-card .el-card__header').allInnerTexts()
    check(`${name} 总结四块卡片`, titles.includes('本学期亮点') && titles.length >= 4, titles.join('/'))
    await noHScroll(page, `${name} 总结页`)
    await page.screenshot({ path: `${SHOTS}/m7-${name}-6-summary.png` })
  } finally {
    await page.close()
  }
}

async function viewportPass(browser, vp) {
  console.log(`\n===== ${vp.name} ${vp.width}x${vp.height} =====`)
  const examName = `M7-E2E考试${vp.name}`
  const mk = async () => (await browser.newContext(
    { viewport: { width: vp.width, height: vp.height }, isMobile: vp.mobile, hasTouch: vp.mobile }))
  // 单角色异常只记 FAIL，不炸整轮
  const run = async (flow, label) => {
    const ctx = await mk()
    try {
      await flow(ctx, vp.name, examName)
    } catch (e) {
      check(`${vp.name} ${label} 流程异常`, false, String(e).split('\n').slice(0, 3).join(' | '))
    } finally {
      await ctx.close()
    }
  }
  await run(adminFlow, 'admin')
  await run(wangFlow, 'wang')
  await run(zhaoFlow, 'zhao')
}

console.log('[信封] 快照 …')
dump()
let rc = 1
const browser = await chromium.launch({ channel: 'chrome' })
try {
  await viewportPass(browser, { name: 'desktop', width: 1440, height: 900, mobile: false })
  await viewportPass(browser, { name: 'mobile', width: 390, height: 844, mobile: true })
  rc = fail === 0 ? 0 : 1
} finally {
  await browser.close()
  console.log('[信封] 恢复数据库 …')
  restore()
  fs.rmSync(SNAP, { force: true })
}
console.log(`\nRESULT: ${rc === 0 ? 'PASS' : 'FAIL'}  pass=${pass} fail=${fail}`)
process.exit(rc)
