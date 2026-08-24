<template>
  <div class="page">
    <motion.h2 class="page-title" :initial="{ opacity: 0, x: -16 }" :animate="{ opacity: 1, x: 0 }"
      :transition="{ type: 'spring', stiffness: 400, damping: 32 }"><el-icon><Setting /></el-icon>系统管理</motion.h2>
    <el-tabs v-model="tab">
      <el-tab-pane label="教师与任课" name="teacher"><TeacherTab /></el-tab-pane>
      <el-tab-pane label="年级与班级" name="org"><OrgTab /></el-tab-pane>
      <el-tab-pane label="学生" name="student"><StudentTab /></el-tab-pane>
      <el-tab-pane label="学期" name="term"><TermTab /></el-tab-pane>
      <el-tab-pane label="育人指标" name="indicator"><IndicatorTab /></el-tab-pane>
      <el-tab-pane label="报告模板" name="template"><TemplateTab /></el-tab-pane>
      <el-tab-pane label="审计日志" name="audit"><AuditTab /></el-tab-pane>
      <el-tab-pane label="AI 用量" name="aiUsage"><AiUsageTab /></el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { motion } from 'motion-v'
import TeacherTab from '../components/admin/TeacherTab.vue'
import OrgTab from '../components/admin/OrgTab.vue'
import StudentTab from '../components/admin/StudentTab.vue'
import TermTab from '../components/admin/TermTab.vue'
import IndicatorTab from '../components/admin/IndicatorTab.vue'
import TemplateTab from '../components/admin/TemplateTab.vue'
import AuditTab from '../components/admin/AuditTab.vue'
import AiUsageTab from '../components/admin/AiUsageTab.vue'

const tab = ref('teacher')
</script>

<style scoped>
:deep(.el-tabs__header) { margin: 0 0 16px; }
:deep(.el-tabs__nav-wrap::after) { height: 1px; background: var(--el-border-color-lighter); }
:deep(.el-tabs__item) { font-size: 15px; }
</style>
