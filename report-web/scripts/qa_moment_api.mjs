// 微光信箱 API 自测：登录 → 建 2 条微光（不同场景标签）→ 班级/学生列表 → 照片流混排 → 文件流
import { readFileSync } from 'node:fs'

const BASE = 'http://localhost:8080'
const Authorization = { Authorization: '' }
const j = (r) => r.json()

const login = await fetch(`${BASE}/api/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'litao', password: 'aischool123' }),
}).then(j)
Authorization.Authorization = 'Bearer ' + login.data.token
console.log('login:', login.data.user.realName, login.data.user.role)

const classes = await fetch(`${BASE}/api/meta/my-classes`, { headers: Authorization }).then(j)
const classId = classes.data[0].id
const stus = await fetch(`${BASE}/api/student/list?classId=${classId}&page=1&size=10`, { headers: Authorization }).then(j)
const studentIds = stus.data.records.slice(0, 2).map((s) => s.id)
console.log('class:', classes.data[0].name, 'students:', studentIds.join(','))

const photo = readFileSync('D:/srp_project/AI_school/report-web/public/campus-pano.jpg')
async function createMoment(sceneTag, note, ids) {
  const fd = new FormData()
  fd.append('photo', new Blob([photo], { type: 'image/jpeg' }), 'photo.jpg')
  fd.append('classId', String(classId))
  fd.append('sceneTag', sceneTag)
  fd.append('note', note)
  for (const id of ids) fd.append('studentIds', String(id))
  const r = await fetch(`${BASE}/api/moment`, { method: 'POST', headers: Authorization, body: fd }).then(j)
  console.log(`create [${sceneTag}]:`, JSON.stringify(r))
  return r
}
const r1 = await createMoment('课堂专注', 'qa：小组讨论专注投入', studentIds)
await createMoment('作业优秀', 'qa：作业书写工整', [studentIds[0]])

// 校验：参数缺失应报错
const bad = new FormData()
bad.append('photo', new Blob([photo], { type: 'image/jpeg' }), 'photo.jpg')
bad.append('classId', String(classId))
bad.append('sceneTag', '助人为乐')
const badResp = await fetch(`${BASE}/api/moment`, { method: 'POST', headers: Authorization, body: bad }).then(j)
console.log('missing studentIds →', badResp.code, badResp.message)

const byClass = await fetch(`${BASE}/api/moment/class?classId=${classId}&limit=20`, { headers: Authorization }).then(j)
console.log('class list:', byClass.data.length, '条; 首条:',
  byClass.data[0]?.sceneTag, byClass.data[0]?.students.map((s) => s.name).join('、'),
  byClass.data[0]?.teacherName, byClass.data[0]?.photoUrl)

const byStu = await fetch(`${BASE}/api/moment/student?studentId=${studentIds[0]}`, { headers: Authorization }).then(j)
console.log(`student ${studentIds[0]} 微光:`, byStu.data.length, '条')

const feed = await fetch(`${BASE}/api/feed?limit=50`, { headers: Authorization }).then(j)
const momentFeed = feed.data.filter((f) => f.type === '微光')
console.log('feed 微光条数:', momentFeed.length, '; 首条:', momentFeed[0]?.title,
  momentFeed[0]?.studentNames, momentFeed[0]?.photoUrl)

// 文件流：应返回 jpeg 字节
const fileUrl = byClass.data[0].photoUrl
const file = await fetch(BASE + fileUrl, { headers: Authorization })
console.log('file:', file.status, file.headers.get('content-type'),
  (await file.arrayBuffer()).byteLength, 'bytes')

// 越权删除：另一教师 token 删 litao 的微光应 403
const other = await fetch(`${BASE}/api/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'wanglaoshi', password: 'aischool123' }),
}).then(j)
const delResp = await fetch(`${BASE}/api/moment/${r1.data.momentId}`, {
  method: 'DELETE', headers: { Authorization: 'Bearer ' + other.data.token },
}).then(j)
console.log('other teacher delete →', delResp.code, delResp.message)
console.log('DONE')
