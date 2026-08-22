<template>
  <div class="page">
    <motion.h2 class="page-title" :initial="{ opacity: 0, x: -16 }" :animate="{ opacity: 1, x: 0 }"
      :transition="{ type: 'spring', stiffness: 400, damping: 32 }"><el-icon><Document /></el-icon>报告列表</motion.h2>
    <div class="toolbar">
      <el-radio-group v-if="auth.role === 'ADMIN'" v-model="scope" @change="onScopeChange">
        <el-radio-button value="class">班级</el-radio-button>
        <el-radio-button value="grade">全年级</el-radio-button>
      </el-radio-group>
      <el-select v-if="scope === 'grade'" v-model="gradeId" placeholder="年级" style="min-width: 140px">
        <el-option v-for="g in grades" :key="g.id" :label="g.name" :value="g.id" />
      </el-select>
      <el-select v-else v-model="classId" placeholder="班级" style="min-width: 140px" @change="reload">
        <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="termId" placeholder="学期" style="min-width: 160px" @change="reload">
        <el-option v-for="t in terms" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <el-button v-if="scope === 'grade'" type="primary" :loading="batching" :disabled="!gradeId || !termId" @click="startGrade">
        批量生成全年级
      </el-button>
      <el-button v-else type="primary" :loading="batching" :disabled="!classId || !termId" @click="startBatch">
        批量生成全班
      </el-button>
      <el-link v-if="scope === 'class'" type="primary" @click="$router.push('/')">看任务进度 →</el-link>
    </div>

    <el-table :data="rows" size="small" border>
      <el-table-column prop="studentNo" label="学号" width="90" />
      <el-table-column prop="name" label="学生" min-width="90" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="tagType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="error" label="错误" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button
            v-if="row.status !== '成功'"
            size="small" type="primary"
            :loading="generating === row.studentId"
            :disabled="generating !== null && generating !== row.studentId"
            @click="generateOne(row)"
          >{{ row.status === '失败' ? '重新生成' : '生成' }}</el-button>
          <el-button v-if="row.status === '成功'" size="small" type="primary" @click="preview(row)">预览</el-button>
          <el-button v-if="row.status === '成功'" size="small" @click="download(row)">下载</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { motion } from 'motion-v'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, fetchBlob } from '../api/http'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const classes = ref<{ id: number; name: string }[]>([])
const grades = ref<{ id: number; name: string }[]>([])
const terms = ref<{ id: number; name: string }[]>([])
const classId = ref<number>()
const gradeId = ref<number>()
const termId = ref<number>()
const scope = ref<'class' | 'grade'>('class')
const rows = ref<{ studentId: number; studentNo: string; name: string; status: string; error?: string; reportId?: number }[]>([])
const generating = ref<number | null>(null)
const batching = ref(false)

function tagType(s: string) {
  return s === '成功' ? 'success' : s === '失败' ? 'danger' : s === '未生成' ? 'info' : 'warning'
}

async function init() {
  classes.value = await api('/api/meta/my-classes')
  terms.value = await api('/api/meta/terms')
  termId.value = terms.value[0]?.id
  if (auth.role === 'ADMIN') {
    grades.value = await api('/api/meta/grades')
    gradeId.value = grades.value[0]?.id
  }
  if (classes.value.length) {
    classId.value = classes.value[0].id
    await reload()
  }
}

function onScopeChange() {
  rows.value = []
}

async function reload() {
  if (!classId.value || !termId.value) return
  const stu = await api<{ records: { id: number; studentNo: string; name: string }[] }>(
    `/api/student/list?classId=${classId.value}&page=1&size=100`,
  )
  const reports = await api<{ studentId: number; status: string; error?: string; reportId?: number }[]>(
    `/api/report/list?classId=${classId.value}&termId=${termId.value}`,
  )
  const byStu = new Map(reports.map((r) => [r.studentId, r]))
  rows.value = stu.records.map((s) => {
    const r = byStu.get(s.id)
    return { studentId: s.id, studentNo: s.studentNo, name: s.name, status: r?.status ?? '未生成', error: r?.error, reportId: r?.reportId }
  })
}

/** 单份生成：提交后轮询任务到终态（验收② 30s 内出 PDF） */
async function generateOne(row: { studentId: number; name: string }) {
  if (!termId.value) return
  generating.value = row.studentId
  try {
    const t = await api<{ taskId: number }>('/api/report/generate', {
      method: 'POST',
      json: { studentId: row.studentId, termId: termId.value },
    })
    const start = Date.now()
    for (;;) {
      await new Promise((r) => setTimeout(r, 2000))
      const p = await api<{ status: string; done: number; failed: number }>(`/api/report/task/${t.taskId}`)
      if (['成功', '失败', '部分失败'].includes(p.status) || Date.now() - start > 60_000) {
        ElMessage[p.failed > 0 ? 'error' : 'success'](`${row.name} ${p.status}（${Math.round((Date.now() - start) / 1000)}s）`)
        break
      }
    }
    await reload()
  } finally {
    generating.value = null
  }
}

async function startBatch() {
  if (!classId.value || !termId.value) return
  batching.value = true
  try {
    const t = await api<{ taskId: number }>('/api/report/generate-batch', {
      method: 'POST',
      json: { classId: classId.value, termId: termId.value },
    })
    ElMessage.success(`批量任务 #${t.taskId} 已创建`)
    router.push('/')
  } finally {
    batching.value = false
  }
}

/** 全年级批量（仅管理员）：任务页看进度 */
async function startGrade() {
  if (!gradeId.value || !termId.value) return
  batching.value = true
  try {
    const t = await api<{ taskId: number }>('/api/report/generate-grade', {
      method: 'POST',
      json: { gradeId: gradeId.value, termId: termId.value },
    })
    ElMessage.success(`年级批量任务 #${t.taskId} 已创建`)
    router.push('/')
  } finally {
    batching.value = false
  }
}

function preview(row: { reportId?: number }) {
  router.push(`/reports/${row.reportId}/preview`)
}

async function download(row: { name: string; reportId?: number }) {
  if (!row.reportId) return
  const blob = await fetchBlob(`/api/report/file/${row.reportId}?disposition=attachment`)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${row.name}-素质报告单.pdf`
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(init)
</script>
