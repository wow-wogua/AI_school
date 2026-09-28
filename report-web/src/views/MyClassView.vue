<template>
  <div class="app-page myclass">
    <!-- 头区：班级名 + 班主任 -->
    <div class="app-hero hero">
      <div class="hero-row">
        <div>
          <h1>班级管理</h1>
          <p>{{ className || '加载中…' }} · 班主任 {{ headName || '未设置' }}</p>
        </div>
      </div>
    </div>

    <!-- 名单管理：导入 / 调出 / 移除 -->
    <div class="app-sec">名单管理</div>
    <div class="app-card tex-a sec-list">
      <div class="ops">
        <van-uploader :after-read="onImportFile" :max-count="1" accept=".xlsx" deletable>
          <button class="op-btn main" type="button"><van-icon name="orders-o" /> 导入 Excel</button>
        </van-uploader>
        <button class="op-btn" type="button" @click="downloadTemplate"><van-icon name="down" /> 模板下载</button>
      </div>
      <p class="op-tip">班级列留空或填「{{ className }}」即可；已录入学生调出/移除在下方操作。</p>

      <div v-for="s in students" :key="s.id" class="row">
        <div class="r-info">
          <b>{{ s.name }}</b>
          <small>{{ s.studentNo || '无学号' }}<template v-if="s.status && s.status !== '在读'"> · {{ s.status }}</template></small>
        </div>
        <div class="r-btns">
          <button class="mini out" type="button" @click="transferOut(s)">调出</button>
          <button class="mini del" type="button" @click="removeStudent(s)">移除</button>
        </div>
      </div>
      <van-empty v-if="!students.length" image-size="72" description="本班暂无学生" />
    </div>

    <!-- 本班任课 -->
    <div class="app-sec">本班任课</div>
    <div class="app-card tex-c sec-teach">
      <div class="row" v-for="t in teaches" :key="t.id">
        <div class="r-info"><b>{{ t.teacherName }}</b><small>{{ t.subjectName }}</small></div>
        <div class="r-btns"><button class="mini del" type="button" @click="removeTeach(t)">删除</button></div>
      </div>
      <van-empty v-if="!teaches.length" image-size="60" description="未设置任课" />
      <div class="add-row">
        <button class="pick" type="button" @click="openTeacherPick()">{{ newTeachName || '选教师' }}</button>
        <button class="pick" type="button" @click="subjOpen = true">{{ newSubjName || '选科目' }}</button>
        <button class="mini add" type="button" @click="addTeach">添加</button>
      </div>
    </div>

    <!-- 交接班 -->
    <div class="app-sec">交接班</div>
    <div class="app-card tex-f sec-hand">
      <p class="hand-tip">交接后：新班主任立即就位并接管本班管理权；您卸任（不再担任任何班主任时降回普通教师）；本班未用邀请码全部作废，请提醒新班主任重新生成。</p>
      <div class="add-row">
        <button class="pick wide" type="button" @click="openTeacherPick(true)">{{ handoverName || '选择新班主任' }}</button>
        <button class="mini main-btn" type="button" @click="doHandover">一键交接</button>
      </div>
    </div>

    <CampusSkyline />

    <!-- 教师选择（搜索+列表） -->
    <van-popup v-model:show="teacherOpen" position="bottom" round :style="{ maxHeight: '80%' }">
      <div class="tp">
        <div class="tp-search"><van-icon name="search" /><input v-model="tq" placeholder="搜索姓名" @input="debouncedTeachers" /></div>
        <div class="tp-list">
          <div v-for="t in teacherOpts" :key="t.id" class="tp-row" @click="pickTeacher(t)">
            <b>{{ t.realName }}</b><small>{{ t.username }} · {{ roleName(t.role) }}</small>
          </div>
          <van-empty v-if="!teacherOpts.length" image-size="60" description="无匹配教师" />
        </div>
      </div>
    </van-popup>

    <!-- 科目选择 -->
    <van-popup v-model:show="subjOpen" position="bottom" round>
      <van-picker title="选择科目" :columns="subjColumns" @confirm="onSubj" @cancel="subjOpen = false" />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import type { UploaderFileListItem } from 'vant'
import { api, apiForm, fetchBlob } from '../api/http'
import { useAuthStore } from '../stores/auth'
import CampusSkyline from '../components/CampusSkyline.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const classId = ref<number>()
const className = ref('')
// 入口只对 HEAD_TEACHER 开放，且后端校验必须是本班班主任 → 班主任即当前用户
const headName = computed(() => auth.realName)
const students = ref<any[]>([])
const teaches = ref<any[]>([])

async function loadBase() {
  const mine = await api<any[]>('/api/meta/my-classes')
  const id = Number(route.query.classId)
  classId.value = id || mine[0]?.id
  className.value = (route.query.name as string) || mine.find((c: any) => c.id === classId.value)?.name || ''
  if (!classId.value) {
    showToast('未找到您的班级')
    return
  }
  await loadStudents()
  reloadTeach()
}

async function reloadTeach() {
  if (!classId.value) return
  try {
    teaches.value = await api<any[]>(`/api/my-class/teach/list?classId=${classId.value}`)
  } catch {
    teaches.value = []
  }
}

/* ── 名单：导入 / 模板 / 调出 / 移除 ── */
async function onImportFile(item: UploaderFileListItem | UploaderFileListItem[]) {
  const f = (Array.isArray(item) ? item[0] : item).file as File
  if (!f || !classId.value) return
  const fd = new FormData()
  fd.append('classId', String(classId.value))
  fd.append('file', f)
  const r = await apiForm<any>('/api/my-class/students/import', fd)
  await loadStudents()
  if (r.failed > 0) {
    showToast(`导入 ${r.inserted} 成功 / ${r.failed} 失败：${r.errors[0]?.row} 行 ${r.errors[0]?.reason}`)
  } else {
    showSuccessToast(`导入成功 ${r.inserted} 人`)
  }
}

async function loadStudents() {
  if (!classId.value) return
  const cls = await api<{ records: any[] }>(`/api/student/list?classId=${classId.value}&page=1&size=100`)
  students.value = cls.records
}

async function downloadTemplate() {
  if (!classId.value) return
  const blob = await fetchBlob(`/api/my-class/students/import-template?classId=${classId.value}`)
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = '本班学生导入模板.xlsx'
  a.click()
  URL.revokeObjectURL(a.href)
}

async function transferOut(s: any) {
  await showConfirmDialog({ title: '调出确认', message: `将「${s.name}」调出本班？学生档案保留，退出在读名单。` })
  await api(`/api/my-class/student/${s.id}/transfer-out`, { method: 'PUT' })
  showSuccessToast('已调出')
  await loadStudents()
}

async function removeStudent(s: any) {
  await showConfirmDialog({ title: '移除确认', message: `删除学生「${s.name}」？仅可移除无任何成长数据的误录学生，有数据请改用「调出」。` })
  await api(`/api/my-class/student/${s.id}`, { method: 'DELETE' })
  showSuccessToast('已移除')
  await loadStudents()
}

/* ── 本班任课 ── */
const subjOpen = ref(false)
const subjects = ref<any[]>([])
const subjColumns = computed(() => subjects.value.map((s) => ({ text: s.name, value: s.id })))
const newTeachId = ref<number>()
const newTeachName = ref('')
const newSubjId = ref<number>()
const newSubjName = ref('')

function onSubj(ev: { selectedOptions?: { text: string; value: number }[] }) {
  const o = ev.selectedOptions?.[0]
  if (o) {
    newSubjId.value = o.value
    newSubjName.value = o.text
  }
  subjOpen.value = false
}

async function addTeach() {
  if (!classId.value || !newTeachId.value || !newSubjId.value) {
    showToast('请先选教师与科目')
    return
  }
  await api('/api/my-class/teach', {
    method: 'POST',
    json: { classId: classId.value, teacherId: newTeachId.value, subjectId: newSubjId.value },
  })
  showSuccessToast('已添加任课')
  newTeachId.value = undefined
  newTeachName.value = ''
  newSubjId.value = undefined
  newSubjName.value = ''
  reloadTeach()
}

async function removeTeach(t: any) {
  await showConfirmDialog({ title: '删除任课', message: `移除「${t.teacherName}」的${t.subjectName}任课？` })
  await api(`/api/my-class/teach/${t.id}?classId=${classId.value}`, { method: 'DELETE' })
  showSuccessToast('已删除')
  reloadTeach()
}

/* ── 教师选择弹层（任课/交接共用） ── */
const teacherOpen = ref(false)
const tq = ref('')
const teacherOpts = ref<any[]>([])
let pickForHandover = false
const handoverId = ref<number>()
const handoverName = ref('')

function openTeacherPick(forHandover = false) {
  pickForHandover = forHandover
  tq.value = ''
  loadTeachers()
  teacherOpen.value = true
}

async function loadTeachers() {
  teacherOpts.value = await api<any[]>(`/api/my-class/teachers?q=${encodeURIComponent(tq.value)}`)
}

function pickTeacher(t: any) {
  if (pickForHandover) {
    handoverId.value = t.id
    handoverName.value = t.realName
  } else {
    newTeachId.value = t.id
    newTeachName.value = t.realName
  }
  teacherOpen.value = false
}

let tqDebounce = 0
function debouncedTeachers() {
  clearTimeout(tqDebounce)
  tqDebounce = window.setTimeout(loadTeachers, 250)
}

/* ── 交接班 ── */
async function doHandover() {
  if (!classId.value || !handoverId.value) {
    showToast('请先选择新班主任')
    return
  }
  await showConfirmDialog({
    title: '交接班确认',
    message: `确认将「${className.value}」交给「${handoverName.value}」？交接后您将卸任，本班未用邀请码全部作废。`,
  })
  const r = await api<any>('/api/my-class/handover', {
    method: 'PUT',
    json: { classId: classId.value, newHeadTeacherId: handoverId.value },
  })
  showSuccessToast(`已交接给 ${r.newHeadTeacherName}`)
  setTimeout(() => {
    auth.logout()
    router.push('/login')
  }, 1200)
}

function roleName(r: string) {
  return r === 'HEAD_TEACHER' ? '班主任' : r === 'LEADER' ? '领导' : '教师'
}

onMounted(async () => {
  subjects.value = await api<any[]>('/api/meta/subjects')
  await loadBase()
})
</script>

<style scoped>
/* C 风格 sub 页（同批27 各页：hero+卡片+sec 竖条），无负 margin（审查批教训） */
.hero-row { display: flex; align-items: flex-start; justify-content: space-between; }
.hero h1 { margin: 4px 0 2px; font-size: 21px; font-weight: 800; }
.hero p { margin: 0; font-size: 12px; color: rgba(255,255,255,.65); }

.sec-list, .sec-teach, .sec-hand { padding: 14px; }
.ops { display: flex; gap: 10px; }
.op-btn { display: flex; align-items: center; gap: 6px; padding: 9px 16px; border: 1px solid var(--app-card-border);
  border-radius: 999px; background: #fff; font-size: 13px; color: var(--app-text-1); cursor: pointer; }
.op-btn.main { background: var(--shine-gradient, #1B2E6B); border: none; color: #fff; font-weight: 600; }
.op-btn .van-icon { font-size: 15px; }
.op-tip { margin: 10px 0 4px; font-size: 11px; color: var(--app-text-3); line-height: 1.6; }

.row { display: flex; align-items: center; justify-content: space-between; gap: 10px;
  padding: 10px 2px; border-bottom: 1px solid var(--app-card-border); }
.row:last-of-type { border-bottom: none; }
.r-info b { font-size: 14px; }
.r-info small { display: block; margin-top: 2px; font-size: 11px; color: var(--app-text-3); }
.r-btns { display: flex; gap: 8px; flex: none; }
.mini { padding: 5px 12px; border: none; border-radius: 999px; font-size: 12px; cursor: pointer; }
.mini.out { background: #FDEEE2; color: #EA580C; }
.mini.del { background: #FEECEC; color: #DC2626; }
.mini.add, .mini.main-btn { background: var(--shine-gradient, #1B2E6B); color: #fff; font-weight: 600; }

.add-row { display: flex; gap: 8px; margin-top: 12px; }
.pick { flex: 1; min-width: 0; padding: 9px 10px; border: 1px solid var(--app-card-border);
  border-radius: 10px; background: #fff; font-size: 13px; color: var(--app-text-1);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; cursor: pointer; text-align: left; }
.pick.wide { flex: 2; }

.hand-tip { margin: 0 0 4px; font-size: 12px; line-height: 1.7; color: var(--app-text-2); }

/* 教师选择弹层 */
.tp { padding-bottom: 10px; }
.tp-search { display: flex; align-items: center; gap: 8px; margin: 14px 16px 6px; padding: 9px 12px;
  border-radius: 10px; background: var(--app-bg, #f5f7fb); color: var(--app-text-3); }
.tp-search input { flex: 1; border: none; outline: none; background: none; font-size: 14px; color: var(--app-text-1); }
.tp-list { max-height: 46vh; overflow-y: auto; padding: 0 10px; }
.tp-row { padding: 11px 8px; border-bottom: 1px solid var(--app-card-border); cursor: pointer; }
.tp-row b { font-size: 14px; }
.tp-row small { display: block; margin-top: 2px; font-size: 11px; color: var(--app-text-3); }
</style>
