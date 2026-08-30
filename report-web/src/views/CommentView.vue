<template>
  <div class="page">
    <motion.h2 class="page-title" :initial="{ opacity: 0, x: -16 }" :animate="{ opacity: 1, x: 0 }"
      :transition="{ type: 'spring', stiffness: 400, damping: 32 }"><el-icon><EditPen /></el-icon>班主任寄语</motion.h2>
    <div class="toolbar">
      <el-select v-model="classId" placeholder="班级" style="min-width: 140px" @change="loadStudents">
        <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="studentId" filterable placeholder="选择学生" style="min-width: 160px" @change="load">
        <el-option v-for="s in students" :key="s.id" :label="optLabel(s.id, s.name)" :value="s.id" />
      </el-select>
      <el-select v-model="termId" placeholder="学期" style="min-width: 160px" @change="load">
        <el-option v-for="t in terms" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <el-button :disabled="!studentId" :loading="!!isRunning" @click="makeDraft">AI 生成草稿</el-button>
      <el-button :disabled="!students.length || !termId" :loading="batchLoading" @click="batchAll">本班全部生成</el-button>
      <el-button :disabled="!classId || !termId" @click="exportXlsx">导出本班寄语</el-button>
      <el-tag v-if="isRunning" size="small" type="primary">{{ cur?.status === '排队' ? '排队中' : '生成中' }}，可切页后台继续</el-tag>
      <el-tag v-else-if="status !== '无'" size="small">{{ status }}</el-tag>
    </div>

    <el-card v-if="studentId">
      <template #header>班主任寄语（AI 只产草稿，人工编辑确认后生效）</template>
      <el-input v-model="content" type="textarea" :rows="8" placeholder="点击「AI 生成草稿」或直接输入" />
      <div style="margin-top: 12px; display: flex; gap: 8px; flex-wrap: wrap">
        <el-button :disabled="!content" @click="save(false)">保存</el-button>
        <el-button type="primary" :disabled="!content" @click="save(true)">确认生效</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { motion } from 'motion-v'
import { ElMessage } from 'element-plus'
import { api, fetchBlob } from '../api/http'
import { saveFile } from '../api/nativeShare'
import { useAiTasksStore } from '../stores/aiTasks'

const store = useAiTasksStore()
const route = useRoute()

const classes = ref<{ id: number; name: string }[]>([])
const terms = ref<{ id: number; name: string }[]>([])
const students = ref<{ id: number; name: string }[]>([])
const classId = ref<number>()
const termId = ref<number>()
const studentId = ref<number>()
const content = ref('')
const status = ref('无')
const batchLoading = ref(false)

/** 当前学生+学期的寄语任务（全局轮询驱动，切页回来状态仍在） */
const cur = computed(() => studentId.value && termId.value
  ? store.byKey.get(`COMMENT-${studentId.value}-${termId.value}`) : undefined)
const isRunning = computed(() => cur.value && (cur.value.status === '排队' || cur.value.status === '生成中'))

let myTaskId = 0        // 本页提交的任务：完成即回填草稿
let appliedTaskId = 0   // 已回填过的任务：防轮询重复回填

/** 回填某条已完成任务的草稿（点击生成即明确意图，直接覆盖编辑框，教师可再改） */
async function applyDraft(t0: { taskId: number; status?: string; source?: string; result?: Record<string, unknown> }) {
  if (t0.status !== '成功' || appliedTaskId === t0.taskId) return
  const d = t0.result ? t0 : await store.fetchDetail(t0.taskId)
  const draft = (d.result as { draft?: string } | undefined)?.draft
  if (!draft) return
  appliedTaskId = t0.taskId
  content.value = draft
  status.value = 'AI草稿'
  ElMessage.success(d.source === 'template' ? '已按模板生成草稿（未配置大模型 API）' : '大模型草稿已生成')
}

// 任务完成即回填：本页提交的任务，或在本页见证「排队/生成中 → 成功」的任务（含切页回来时仍在跑的）
watch(cur, (t, prev) => {
  if (!t || t.status !== '成功' || appliedTaskId === t.taskId) return
  const progressed = !!prev && prev.taskId === t.taskId && (prev.status === '排队' || prev.status === '生成中')
  if (t.taskId !== myTaskId && !progressed) return
  applyDraft(t)
})

async function init() {
  classes.value = await api('/api/meta/my-classes')
  terms.value = await api('/api/meta/terms')
  termId.value = terms.value[0]?.id
  if (classes.value.length) {
    classId.value = classes.value[0].id
    await loadStudents()
  }
  // 任务面板点进来的预选（带 studentId/termId 查询参数）
  if (route.query.termId) {
    const t = terms.value.find((x) => x.id === Number(route.query.termId))
    if (t) termId.value = t.id
  }
  if (route.query.studentId && students.value.some((s) => s.id === Number(route.query.studentId))) {
    studentId.value = Number(route.query.studentId)
    await load()
  }
  await applyPanelTask()
}

/**
 * 生成中心点进来的任务（query 带 taskId）：已完成则直接回填其草稿——
 * 不走 load() 的「已生效内容优先」，否则切页回来永远看到旧生效内容而非本次新生成的草稿。
 * 任务还在跑则先展示现状，完成后由上方 watch 跃迁回填。
 */
async function applyPanelTask() {
  const q = route.query
  if (!q.taskId || !q.studentId) return
  if (q.termId) {
    const t = terms.value.find((x) => x.id === Number(q.termId))
    if (t && t.id !== termId.value) termId.value = t.id
  }
  if (!students.value.some((s) => s.id === Number(q.studentId))) return
  if (studentId.value !== Number(q.studentId)) {
    studentId.value = Number(q.studentId)
    await load()
  }
  applyDraft(await store.fetchDetail(Number(q.taskId)))
}

// 已停留本页时再点生成中心的其他任务：query 变化同样预选并回填
watch(() => route.query.taskId, () => applyPanelTask())

/** 学生下拉标签：批量时直接在选项里看到每个学生的任务进度 */
function optLabel(id: number, name: string) {
  const t = termId.value ? store.byKey.get(`COMMENT-${id}-${termId.value}`) : undefined
  if (t && (t.status === '排队' || t.status === '生成中')) return `${name} · ${t.status}`
  return name
}

async function loadStudents() {
  if (!classId.value) return
  const d = await api<{ records: { id: number; name: string }[] }>(
    `/api/student/list?classId=${classId.value}&page=1&size=100`,
  )
  students.value = d.records
  studentId.value = undefined
  content.value = ''
  status.value = '无'
}

async function load() {
  if (!studentId.value || !termId.value) return
  const c = await api<{ content: string; aiDraft: string; status: string }>(
    `/api/ai/comment?studentId=${studentId.value}&termId=${termId.value}`,
  )
  // 已生效内容优先；没有生效内容时回显后台任务生成的草稿（切页/重开浏览器后仍可见）
  content.value = c.content || c.aiDraft || ''
  status.value = c.status ?? '无'
}

async function makeDraft() {
  if (!studentId.value || !termId.value) return
  myTaskId = await store.submit('COMMENT', studentId.value, termId.value)
}

/** 本班全部生成：逐个提交入队（后端去重，重复点击安全），进度看右上角「AI 任务」 */
async function batchAll() {
  if (!termId.value || !students.value.length) return
  batchLoading.value = true
  try {
    for (const s of students.value) {
      await store.submit('COMMENT', s.id, termId.value)
    }
    ElMessage.success(`已提交 ${students.value.length} 位学生的寄语任务，后台生成中，可切换页面；进度见右上角「生成中心」`)
  } finally {
    batchLoading.value = false
  }
}

/** 导出本班寄语（班级×学期 → xlsx，含生效内容与 AI 草稿） */
async function exportXlsx() {
  const blob = await fetchBlob(`/api/ai/comment/export?classId=${classId.value}&termId=${termId.value}`)
  const cls = classes.value.find((c) => c.id === classId.value)?.name ?? ''
  const tm = terms.value.find((t) => t.id === termId.value)?.name ?? ''
  await saveFile(blob, `寄语_${cls}_${tm}.xlsx`)
}

async function save(confirm: boolean) {
  if (!studentId.value || !termId.value) return
  const r = await api<{ status: string }>('/api/ai/comment', {
    method: 'PUT',
    json: { studentId: studentId.value, termId: termId.value, content: content.value, confirm },
  })
  status.value = r.status
  ElMessage.success(confirm ? '已确认生效，报告单将使用该寄语' : '已保存')
}

onMounted(init)
</script>
