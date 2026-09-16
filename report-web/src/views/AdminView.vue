<template>
  <div class="page">
    <motion.h2 class="page-title" :initial="{ opacity: 0, x: -16 }" :animate="{ opacity: 1, x: 0 }"
      :transition="{ type: 'spring', stiffness: 400, damping: 32 }"><el-icon><Setting /></el-icon>系统管理</motion.h2>
    <div v-if="online.users !== null" class="online-bar">
      <span class="dot"></span>
      当前在线 <b>{{ online.onlineCount }}</b> 人
      <span class="sep">·</span>
      24 小时活跃 <b>{{ online.dailyCount }}</b> 人
      <el-tooltip v-if="online.users.length" placement="bottom">
        <template #content>
          <div v-for="u in online.users" :key="u.userId">{{ u.realName }}（{{ u.username }}）· {{ ago(u.lastActiveMs) }}</div>
        </template>
        <span class="who">在线名单</span>
      </el-tooltip>
    </div>
    <el-tabs v-model="tab">
      <el-tab-pane label="教师与任课" name="teacher"><TeacherTab /></el-tab-pane>
      <el-tab-pane label="教师档案" name="teacherProfile"><TeacherProfileTab /></el-tab-pane>
      <el-tab-pane label="年级与班级" name="org"><OrgTab /></el-tab-pane>
      <el-tab-pane label="学生" name="student"><StudentTab /></el-tab-pane>
      <el-tab-pane label="学期" name="term"><TermTab /></el-tab-pane>
      <el-tab-pane label="育人指标" name="indicator"><IndicatorTab /></el-tab-pane>
      <el-tab-pane label="报告模板" name="template"><TemplateTab /></el-tab-pane>
      <el-tab-pane label="审计日志" name="audit"><AuditTab /></el-tab-pane>
      <el-tab-pane label="AI 用量" name="aiUsage"><AiUsageTab /></el-tab-pane>
      <el-tab-pane label="版本更新" name="appRelease"><AppReleaseTab /></el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { motion } from 'motion-v'
import { api } from '../api/http'
import TeacherTab from '../components/admin/TeacherTab.vue'
import TeacherProfileTab from '../components/admin/TeacherProfileTab.vue'
import OrgTab from '../components/admin/OrgTab.vue'
import StudentTab from '../components/admin/StudentTab.vue'
import TermTab from '../components/admin/TermTab.vue'
import IndicatorTab from '../components/admin/IndicatorTab.vue'
import TemplateTab from '../components/admin/TemplateTab.vue'
import AuditTab from '../components/admin/AuditTab.vue'
import AiUsageTab from '../components/admin/AiUsageTab.vue'
import AppReleaseTab from '../components/admin/AppReleaseTab.vue'

/* 支持 ?tab= 直达指定页签（首页快捷功能「教师档案」入口用） */
const tab = ref((useRoute().query.tab as string) || 'teacher')

/* 在线统计条：30 秒轮询，失败静默（不影响管理端使用） */
const online = reactive<{ onlineCount: number; dailyCount: number; users: any[] | null }>({
  onlineCount: 0, dailyCount: 0, users: null,
})
let onlineTimer: number | undefined

async function loadOnline() {
  try {
    const d = await api<{ onlineCount: number; dailyCount: number; onlineUsers: any[] }>('/api/admin/online')
    online.onlineCount = d.onlineCount
    online.dailyCount = d.dailyCount
    online.users = d.onlineUsers
  } catch { /* 静默 */ }
}

function ago(ms: number) {
  const s = Math.max(0, Math.round((Date.now() - ms) / 1000))
  return s < 60 ? `${s} 秒前活跃` : `${Math.round(s / 60)} 分钟前活跃`
}

onMounted(() => {
  loadOnline()
  onlineTimer = window.setInterval(loadOnline, 30_000)
})
onUnmounted(() => window.clearInterval(onlineTimer))
</script>

<style scoped>
.online-bar { display: flex; align-items: center; gap: 8px; margin: 0 0 14px; font-size: 13px; color: var(--el-text-color-secondary); }
.online-bar b { color: var(--el-color-primary); font-size: 15px; }
.online-bar .sep { color: var(--el-border-color-darker); }
.online-bar .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--el-color-success); animation: online-pulse 2s infinite; }
.who { cursor: help; text-decoration: underline dotted; }
@keyframes online-pulse {
  0% { box-shadow: 0 0 0 0 rgba(103, 194, 58, 0.45); }
  70% { box-shadow: 0 0 0 7px rgba(103, 194, 58, 0); }
  100% { box-shadow: 0 0 0 0 rgba(103, 194, 58, 0); }
}
:deep(.el-tabs__header) { margin: 0 0 16px; }
:deep(.el-tabs__nav-wrap::after) { height: 1px; background: var(--el-border-color-lighter); }
:deep(.el-tabs__item) { font-size: 15px; }
</style>
