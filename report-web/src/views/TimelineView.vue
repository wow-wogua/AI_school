<template>
  <div class="page">
    <motion.h2 class="page-title" :initial="{ opacity: 0, x: -16 }" :animate="{ opacity: 1, x: 0 }"
      :transition="{ type: 'spring', stiffness: 400, damping: 32 }"><el-icon><Timer /></el-icon>成长时间轴</motion.h2>
    <div class="toolbar">
      <el-select v-model="classId" placeholder="班级" style="min-width: 140px" @change="loadStudents">
        <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="studentId" filterable placeholder="选择学生" style="min-width: 160px" @change="load">
        <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-select v-model="termId" placeholder="学期" style="min-width: 160px" @change="load">
        <!-- 0=在校全期（批14 生命周期档案）：跨全部学期聚合 -->
        <el-option key="full" label="在校全期" :value="0" />
        <el-option v-for="t in terms" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <el-tag v-if="studentId" class="stat-chip" size="small">{{ statText }}</el-tag>
    </div>

    <el-card v-if="studentId">
      <template #header>{{ termId === 0 ? '成长档案 · 在校全期' : '成长事件时间轴' }}</template>
      <p v-if="termId === 0 && full" class="full-meta">
        {{ full.student.name }}（{{ full.student.className }}{{ full.student.studentNo ? ' · ' + full.student.studentNo : '' }}）<template v-if="full.student.enrollDate"> · 入学 {{ full.student.enrollDate }}</template>
        <template v-if="full.truncated"> · 事件较多，仅显示最近 500 条</template>
      </p>
      <el-timeline v-if="events.length">
        <el-timeline-item v-for="(e, i) in events" :key="i" :type="colorOf(e.type)" :timestamp="e.time" placement="top">
          <b>{{ e.title }}</b>
          <span style="margin-left: 8px; color: #606266">{{ e.detail }}</span>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="该时段暂无事件记录" :image-size="80" />
    </el-card>
    <el-empty v-else description="请先选择学生" :image-size="80" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { motion } from 'motion-v'
import { api } from '../api/http'

const route = useRoute()

interface Event { type: string; time: string; title: string; detail: string }

const classes = ref<{ id: number; name: string }[]>([])
const terms = ref<{ id: number; name: string }[]>([])
const students = ref<{ id: number; name: string }[]>([])
const classId = ref<number>()
const termId = ref<number>()
const studentId = ref<number>()
const events = ref<Event[]>([])
// 在校全期（批14 生命周期档案）返回的 { student, stats, truncated }
const full = ref<null | { student: Record<string, string>; stats: Record<string, number>; truncated: boolean }>(null)

const counts = computed(() => {
  const c: Record<string, number> = {}
  for (const e of events.value) c[e.type] = (c[e.type] || 0) + 1
  return c
})

const statText = computed(() => {
  if (termId.value !== 0 || !full.value) {
    return `${counts.value.评价 || 0} 评价 · ${counts.value.活动 || 0} 活动 · ${counts.value.荣誉 || 0} 荣誉 · ${counts.value.成绩 || 0} 进步`
  }
  const s = full.value.stats
  return `${s.evaluations} 评价 · ${s.activities} 活动 · ${s.honors} 荣誉 · ${s.moments} 微光 · ${s.reports} 报告`
})

function colorOf(type: string) {
  return ({ 评价: 'info', 活动: 'warning', 荣誉: 'success', 成绩: 'primary', 微光: 'danger' } as Record<string, string>)[type] || 'info'
}

async function init() {
  classes.value = await api('/api/meta/my-classes')
  terms.value = await api('/api/meta/terms')
  termId.value = terms.value[0]?.id
  if (classes.value.length) {
    classId.value = classes.value[0].id
    await loadStudents()
  }
  await preselect()
}

/** 学生详情宫格带学生进来：自动选中该生并拉时间轴 */
async function preselect() {
  const sid = Number(route.query.studentId)
  if (!sid) return
  if (route.query.termId && terms.value.some((t) => t.id === Number(route.query.termId))) {
    termId.value = Number(route.query.termId)
  }
  try {
    const s = await api<{ classId?: number }>(`/api/student/${sid}`)
    if (s.classId && classes.value.some((c) => c.id === s.classId)) {
      classId.value = s.classId
      await loadStudents()
      studentId.value = sid
      await load()
    }
  } catch { /* 深链失效则保持默认视图 */ }
}

async function loadStudents() {
  if (!classId.value) return
  const d = await api<{ records: { id: number; name: string }[] }>(
    `/api/student/list?classId=${classId.value}&page=1&size=100`,
  )
  students.value = d.records
  studentId.value = undefined
  events.value = []
}

async function load() {
  if (!studentId.value || termId.value === undefined) return
  if (termId.value === 0) {
    // 在校全期：生命周期档案端点（跨学期聚合 + 总览统计）
    const d = await api<{ student: Record<string, string>; stats: Record<string, number>; truncated: boolean; events: Event[] }>(
      `/api/timeline/${studentId.value}/lifecycle`,
    )
    full.value = d
    events.value = d.events
    return
  }
  full.value = null
  const d = await api<{ events: Event[] }>(`/api/timeline/${studentId.value}?termId=${termId.value}`)
  events.value = d.events
}

onMounted(init)
</script>

<style scoped>
.full-meta { margin: 0 0 12px; font-size: 13px; color: #606266; }
</style>
