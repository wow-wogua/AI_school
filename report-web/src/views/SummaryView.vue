<template>
  <div class="page">
    <motion.h2 class="page-title" :initial="{ opacity: 0, x: -16 }" :animate="{ opacity: 1, x: 0 }"
      :transition="{ type: 'spring', stiffness: 400, damping: 32 }"><el-icon><MagicStick /></el-icon>成长总结</motion.h2>
    <div class="toolbar">
      <el-select v-model="classId" placeholder="班级" style="min-width: 140px" @change="loadStudents">
        <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="studentId" filterable placeholder="选择学生" style="min-width: 160px">
        <el-option v-for="s in students" :key="s.id" :label="optLabel(s.id, s.name)" :value="s.id" />
      </el-select>
      <el-select v-model="termId" placeholder="学期" style="min-width: 160px">
        <el-option v-for="t in terms" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <el-button type="primary" :disabled="!studentId" :loading="!!isRunning" @click="analyze">AI 分析该学生</el-button>
      <el-tag v-if="isRunning" size="small" type="primary">{{ cur?.status === '排队' ? '排队中' : '分析中' }}，可切页后台继续</el-tag>
    </div>

    <el-empty v-if="!blocks.length && !raw"
      :description="isRunning ? 'AI 分析中，可切换页面，完成后回来自动展示' : '选择学生后点击「AI 分析该学生」'" />
    <el-card v-if="raw && !blocks.length" style="margin-top: 12px">
      <template #header>成长总结</template>
      <div style="white-space: pre-wrap">{{ raw }}</div>
    </el-card>
    <div v-if="blocks.length" class="cards">
      <el-card v-for="(b, i) in blocks" :key="i">
        <template #header>{{ b.title }}</template>
        <div style="white-space: pre-wrap">{{ b.content }}</div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { motion } from 'motion-v'
import { ElMessage } from 'element-plus'
import { api } from '../api/http'
import { useAiTasksStore } from '../stores/aiTasks'

const store = useAiTasksStore()
const route = useRoute()

const classes = ref<{ id: number; name: string }[]>([])
const terms = ref<any[]>([])
const students = ref<{ id: number; name: string }[]>([])
const classId = ref<number>()
const termId = ref<number>()
const studentId = ref<number>()
const raw = ref('')
const blocks = ref<{ title: string; content: string }[]>([])

/** 当前学生+学期的总结任务（总结不落库，恢复/完成展示都走任务结果体） */
const cur = computed(() => studentId.value && termId.value
  ? store.byKey.get(`SUMMARY-${studentId.value}-${termId.value}`) : undefined)
const isRunning = computed(() => cur.value && (cur.value.status === '排队' || cur.value.status === '生成中'))

let renderedTaskId = 0 // 已渲染结果的任务：防轮询重复渲染/重复拉详情

// 切换学生/学期先清展示（本 watch 先于下方 cur watch 声明，同 tick 先执行）
watch([studentId, termId], () => {
  raw.value = ''
  blocks.value = []
  renderedTaskId = 0
})

// 任务完成（含切页/重开浏览器回来后）→ 拉结果体渲染
watch(cur, async (t) => {
  if (!t || t.status !== '成功' || renderedTaskId === t.taskId) return
  renderedTaskId = t.taskId
  const d = t.result ? t : await store.fetchDetail(t.taskId)
  const r = d.result as { raw?: string; blocks?: Record<string, string> } | undefined
  if (!r) return
  raw.value = r.raw ?? ''
  blocks.value = Object.entries(r.blocks ?? {})
    .filter(([, v]) => v)
    .map(([k, v]) => ({ title: k, content: v }))
  if (t.source === 'template') {
    ElMessage.info('未配置大模型 API，已按模板生成')
  }
}, { immediate: true })

/** 学生下拉标签：批量时直接看到每个学生的任务进度 */
function optLabel(id: number, name: string) {
  const t = termId.value ? store.byKey.get(`SUMMARY-${id}-${termId.value}`) : undefined
  if (t && (t.status === '排队' || t.status === '生成中')) return `${name} · ${t.status}`
  return name
}

async function init() {
  const [cs, ts] = await Promise.all([
    api<{ id: number; name: string }[]>('/api/meta/my-classes'),
    api<any[]>('/api/meta/terms'),
  ])
  classes.value = cs
  terms.value = ts
  termId.value = ts.find((t: any) => t.isCurrent === 1)?.id ?? ts[0]?.id
  if (cs.length) {
    classId.value = cs[0].id
    await loadStudents()
  }
  // 任务面板点进来的预选
  if (route.query.termId) {
    const t = ts.find((x: any) => x.id === Number(route.query.termId))
    if (t) termId.value = t.id
  }
  if (route.query.studentId && students.value.some((s) => s.id === Number(route.query.studentId))) {
    studentId.value = Number(route.query.studentId)
  }
}

async function loadStudents() {
  if (!classId.value) return
  const d = await api<{ records: { id: number; name: string }[] }>(
    `/api/student/list?classId=${classId.value}&page=1&size=100`)
  students.value = d.records
  studentId.value = undefined
}

async function analyze() {
  if (!studentId.value || !termId.value) return
  await store.submit('SUMMARY', studentId.value, termId.value)
}

onMounted(init)
</script>

<style scoped>
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 16px; margin-top: 16px; }
.cards :deep(.el-card__body) { line-height: 1.8; color: var(--el-text-color-regular); }
</style>
