<template>
  <div class="page">
    <motion.h2 class="page-title" :initial="{ opacity: 0, x: -16 }" :animate="{ opacity: 1, x: 0 }"
      :transition="{ type: 'spring', stiffness: 400, damping: 32 }"><el-icon><Files /></el-icon>批量任务</motion.h2>
    <div class="toolbar">
      <el-select v-if="tasks.length" v-model="taskId" placeholder="选择任务" style="min-width: 280px" @change="loadDetail">
        <el-option v-for="t in tasks" :key="t.taskId" :label="`#${t.taskId} ${label(t)} ${t.status}`" :value="t.taskId" />
      </el-select>
      <el-button type="primary" @click="$router.push('/reports')">发起生成 →</el-button>
      <el-button v-if="detail && (detail.status === '部分失败' || detail.status === '失败')" type="warning" @click="retry">
        重试失败项
      </el-button>
    </div>

    <el-empty v-if="!tasks.length" description="还没有任务，去报告列表页发起生成" />

    <el-card v-if="detail">
      <template #header>
        任务 #{{ detail.taskId }} {{ detail.status }}（每 3 秒自动刷新）
      </template>
      <el-progress
        :percentage="pct"
        :status="detail.status === '部分失败' || detail.status === '失败' ? 'exception' : detail.status === '成功' ? 'success' : undefined"
      />
      <p style="margin: 8px 0 0">
        总数 {{ detail.total }} · 完成 {{ detail.done }} · 失败 {{ detail.failed }}
        <el-link style="margin-left: 12px" type="primary" @click="$router.push('/reports')">查看报告列表 →</el-link>
      </p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { motion } from 'motion-v'
import { ElMessage } from 'element-plus'
import { api } from '../api/http'

interface TaskView { taskId: number; termId: number; scope: string; targetId: number; status: string; total: number; done: number; failed: number }

const tasks = ref<TaskView[]>([])
const taskId = ref<number>()
const detail = ref<TaskView & { createTime?: string }>()
let timer: number | undefined

const pct = computed(() => (detail.value && detail.value.total ? Math.round((detail.value.done / detail.value.total) * 100) : 0))

function label(t: TaskView) {
  return `${t.scope}(${t.targetId}) 学期${t.termId}`
}

async function loadTasks(pick = false) {
  tasks.value = await api<TaskView[]>('/api/report/task/list?limit=20')
  if (pick && tasks.value.length && !taskId.value) {
    taskId.value = tasks.value[0].taskId
    await loadDetail()
  }
}

async function loadDetail() {
  if (!taskId.value) return
  detail.value = await api<TaskView & { createTime?: string }>(`/api/report/task/${taskId.value}`)
  // 有进行中任务时同步刷新列表状态
  if (tasks.value.some((t) => t.taskId === taskId.value)) {
    const cur = tasks.value.find((t) => t.taskId === taskId.value)
    if (cur && detail.value) Object.assign(cur, detail.value)
  }
}

async function retry() {
  if (!taskId.value) return
  await api(`/api/report/task/${taskId.value}/retry`, { method: 'POST' })
  ElMessage.success('失败项已重新入队')
  loadDetail()
}

onMounted(() => {
  loadTasks(true)
  timer = window.setInterval(loadDetail, 3000)
})
onUnmounted(() => window.clearInterval(timer))
</script>
