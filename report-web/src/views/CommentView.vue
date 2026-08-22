<template>
  <div class="page">
    <motion.h2 class="page-title" :initial="{ opacity: 0, x: -16 }" :animate="{ opacity: 1, x: 0 }"
      :transition="{ type: 'spring', stiffness: 400, damping: 32 }"><el-icon><EditPen /></el-icon>班主任寄语</motion.h2>
    <div class="toolbar">
      <el-select v-model="classId" placeholder="班级" style="min-width: 140px" @change="loadStudents">
        <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="studentId" filterable placeholder="选择学生" style="min-width: 160px" @change="load">
        <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-select v-model="termId" placeholder="学期" style="min-width: 160px" @change="load">
        <el-option v-for="t in terms" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <el-button :disabled="!studentId" :loading="drafting" @click="makeDraft">AI 生成草稿</el-button>
      <el-tag v-if="status !== '无'" size="small">{{ status }}</el-tag>
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
import { onMounted, ref } from 'vue'
import { motion } from 'motion-v'
import { ElMessage } from 'element-plus'
import { api } from '../api/http'

const classes = ref<{ id: number; name: string }[]>([])
const terms = ref<{ id: number; name: string }[]>([])
const students = ref<{ id: number; name: string }[]>([])
const classId = ref<number>()
const termId = ref<number>()
const studentId = ref<number>()
const content = ref('')
const status = ref('无')
const drafting = ref(false)

async function init() {
  classes.value = await api('/api/meta/my-classes')
  terms.value = await api('/api/meta/terms')
  termId.value = terms.value[0]?.id
  if (classes.value.length) {
    classId.value = classes.value[0].id
    await loadStudents()
  }
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
  const c = await api<{ content: string; status: string }>(
    `/api/ai/comment?studentId=${studentId.value}&termId=${termId.value}`,
  )
  content.value = c.content ?? ''
  status.value = c.status ?? '无'
}

async function makeDraft() {
  if (!studentId.value || !termId.value) return
  drafting.value = true
  try {
    const d = await api<{ draft: string; source?: string }>('/api/ai/comment-draft', {
      method: 'POST',
      json: { studentId: studentId.value, termId: termId.value },
    })
    content.value = d.draft
    status.value = 'AI草稿'
    ElMessage.success(d.source === 'template' ? '已按模板生成草稿（未配置大模型 API）' : '大模型草稿已生成')
  } finally {
    drafting.value = false
  }
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
