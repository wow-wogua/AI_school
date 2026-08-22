<template>
  <el-badge :value="store.runningCount" :hidden="!store.runningCount" :max="99" class="ai-panel-badge">
    <el-button link class="ai-panel-btn" @click="store.panelOpen = true">
      <el-icon :class="{ spinning: store.runningCount }"><Loading v-if="store.runningCount" /><MagicStick v-else /></el-icon>
      生成中心
    </el-button>
  </el-badge>
  <el-drawer v-model="store.panelOpen" title="生成中心" size="360px">
    <el-empty v-if="!store.tasks.length" description="还没有任务，去寄语/成长总结页发起" :image-size="72" />
    <div v-else class="task-list">
      <div v-for="t in store.tasks" :key="t.taskId" class="task-item" @click="go(t)">
        <div class="row1">
          <el-tag size="small" :type="typeTag(t.status).type" effect="dark">{{ typeTag(t.status).label }}</el-tag>
          <span class="kind">{{ t.taskType === 'COMMENT' ? '寄语草稿' : '成长总结' }}</span>
          <span class="name">{{ t.studentName || `学生${t.studentId}` }}</span>
          <span class="time">{{ shortTime(t.createTime) }}</span>
        </div>
        <div v-if="t.status === '失败' && t.error" class="err">失败原因：{{ t.error }}</div>
        <div v-if="t.status === '成功'" class="src">{{ t.source === 'llm' ? '大模型生成' : '模板生成（未配置/降级）' }}</div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Loading, MagicStick } from '@element-plus/icons-vue'
import { useAiTasksStore } from '../stores/aiTasks'

const store = useAiTasksStore()
const router = useRouter()

function typeTag(status: string) {
  if (status === '成功') return { type: 'success' as const, label: '成功' }
  if (status === '失败') return { type: 'danger' as const, label: '失败' }
  if (status === '生成中') return { type: 'primary' as const, label: '生成中' }
  return { type: 'info' as const, label: '排队中' }
}

function shortTime(v?: string) {
  if (!v) return ''
  const m = /(\d{2}):(\d{2})/.exec(String(v))
  return m ? `${m[1]}:${m[2]}` : ''
}

/** 点任务直达对应页面并选中该学生（页面读 query 预选） */
function go(t: { taskType: string; studentId: number; termId: number }) {
  store.panelOpen = false
  router.push({ path: t.taskType === 'COMMENT' ? '/comments' : '/summary', query: { studentId: String(t.studentId), termId: String(t.termId) } })
}
</script>

<style scoped>
.ai-panel-btn { color: #a5b4fc; padding: 4px 8px; }
.ai-panel-btn:hover { color: #fff; }
.ai-panel-btn .spinning { animation: ai-spin 1.2s linear infinite; }
@keyframes ai-spin { to { transform: rotate(360deg); } }
.task-list { display: flex; flex-direction: column; gap: 8px; }
.task-item { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 8px 10px; cursor: pointer;
  transition: border-color .2s ease, background-color .2s ease; }
.task-item:hover { border-color: var(--el-color-primary-light-5); background: var(--el-fill-color-light); }
.row1 { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.kind { font-size: 13px; color: var(--el-text-color-regular); }
.name { font-size: 13px; font-weight: 600; }
.time { margin-left: auto; font-size: 12px; color: var(--el-text-color-secondary); }
.err { margin-top: 4px; font-size: 12px; color: var(--el-color-danger); }
.src { margin-top: 4px; font-size: 12px; color: var(--el-text-color-secondary); }
</style>
