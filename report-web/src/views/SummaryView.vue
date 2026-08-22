<template>
  <div class="page">
    <motion.h2 class="page-title" :initial="{ opacity: 0, x: -16 }" :animate="{ opacity: 1, x: 0 }"
      :transition="{ type: 'spring', stiffness: 400, damping: 32 }"><el-icon><MagicStick /></el-icon>成长总结</motion.h2>
    <div class="toolbar">
      <el-select v-model="classId" placeholder="班级" style="min-width: 140px" @change="loadStudents">
        <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="studentId" filterable placeholder="选择学生" style="min-width: 160px">
        <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-select v-model="termId" placeholder="学期" style="min-width: 160px">
        <el-option v-for="t in terms" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <el-button type="primary" :disabled="!studentId" :loading="loading" @click="analyze">AI 分析该学生</el-button>
    </div>

    <el-empty v-if="!blocks.length && !raw" description="选择学生后点击「AI 分析该学生」" />
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
import { onMounted, ref } from 'vue'
import { motion } from 'motion-v'
import { ElMessage } from 'element-plus'
import { api } from '../api/http'

const classes = ref<{ id: number; name: string }[]>([])
const terms = ref<any[]>([])
const students = ref<{ id: number; name: string }[]>([])
const classId = ref<number>()
const termId = ref<number>()
const studentId = ref<number>()
const loading = ref(false)
const raw = ref('')
const blocks = ref<{ title: string; content: string }[]>([])

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
  loading.value = true
  raw.value = ''
  blocks.value = []
  try {
    const d = await api<{ raw: string; blocks: Record<string, string>; source: string }>('/api/ai/summary', {
      method: 'POST',
      json: { studentId: studentId.value, termId: termId.value },
    })
    raw.value = d.raw ?? ''
    blocks.value = Object.entries(d.blocks ?? {})
      .filter(([, v]) => v)
      .map(([k, v]) => ({ title: k, content: v }))
    if (d.source === 'template') {
      ElMessage.info('未配置大模型 API，已按模板生成')
    }
  } finally {
    loading.value = false
  }
}

onMounted(init)
</script>

<style scoped>
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 16px; margin-top: 16px; }
.cards :deep(.el-card__body) { line-height: 1.8; color: var(--el-text-color-regular); }
</style>
