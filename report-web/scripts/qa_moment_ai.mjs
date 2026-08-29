// 验证微光素材注入：模板路的成长总结应含「微光信箱…闪光瞬间」
const BASE = 'http://localhost:8080'
const login = await fetch(`${BASE}/api/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'litao', password: 'aischool123' }),
}).then((r) => r.json())
const H = { Authorization: 'Bearer ' + login.data.token, 'Content-Type': 'application/json' }

// 陈小华(1) 已有 6 条微光；学期 termId=2（2026 春季，当前学期）
const r = await fetch(`${BASE}/api/ai/summary`, {
  method: 'POST', headers: H, body: JSON.stringify({ studentId: 1, termId: 2 }),
}).then((r) => r.json())
console.log('source:', r.data.source)
console.log('raw 首行:', r.data.raw?.split('\n')[0])
console.log('注入成功:', r.data.raw?.includes('微光信箱') && r.data.raw?.includes('闪光瞬间'))

// 无微光学生（class2 的 51 洪雨欣）应不含微光字样且不报错
const r2 = await fetch(`${BASE}/api/ai/summary`, {
  method: 'POST', headers: H, body: JSON.stringify({ studentId: 51, termId: 2 }),
}).then((r) => r.json())
console.log('无微光学生:', r2.code, r2.message, '首行:', r2.data?.raw?.split('\n')[0])
console.log('无微光学生不含微光字样:', r2.data ? !r2.data.raw?.includes('微光') : 'n/a(接口拒绝)')
console.log('DONE')
