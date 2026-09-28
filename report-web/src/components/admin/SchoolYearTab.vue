<template>
  <div>
    <el-alert type="warning" :closable="false" show-icon class="tip"
      title="学年滚动在新学年开学前执行一次：全部年级整体上升一级、最高年级学生标记毕业退场、自动创建新学年学期。执行前请先导出毕业班文件归档（文件归档页签）。" />
    <div class="toolbar">
      <el-button type="primary" :loading="loading" @click="load">查看滚动方案</el-button>
    </div>

    <template v-if="plan">
      <div class="year-line">
        <el-tag size="large" type="info">{{ plan.oldYear }} 学年</el-tag>
        <el-icon class="arrow"><ArrowRight /></el-icon>
        <el-tag size="large" type="danger">{{ plan.newYear }} 学年</el-tag>
        <span class="stat">晋升 {{ plan.promotedClasses }} 班 · 毕业 {{ plan.graduatedStudents }} 人</span>
      </div>

      <el-table :data="plan.grades" size="small" class="plan">
        <el-table-column label="现年级" width="120">
          <template #default="{ row }">{{ row.name }}</template>
        </el-table-column>
        <el-table-column label="动作" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.action === 'PROMOTE'" type="primary" size="small">晋升 → {{ row.targetName }}</el-tag>
            <el-tag v-else-if="row.action === 'GRADUATE'" type="danger" size="small">毕业退场</el-tag>
            <el-tag v-else type="success" size="small">新建（待导入新生）</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="classCount" label="班级数" width="90" />
        <el-table-column prop="studentCount" label="在读人数" width="100" />
      </el-table>

      <div v-if="plan.terms.length" class="sec">新学期计划</div>
      <el-table v-if="plan.terms.length" :data="plan.terms" size="small">
        <el-table-column prop="oldName" label="原学期" />
        <el-table-column prop="newName" label="新学期" />
        <el-table-column prop="start" label="开始" width="120" />
        <el-table-column prop="end" label="结束" width="120" />
      </el-table>
      <div v-else class="sec no-term">未匹配到学期：滚动不会自动建学期，须到「学期」页签手动创建。</div>

      <el-alert v-for="(w, i) in plan.warnings" :key="i" type="info" :closable="false" show-icon
        class="warn" :title="w" />

      <div class="sec">执行滚动</div>
      <div class="roll-row">
        <el-input v-model="confirmYear" :placeholder="`请输入 ${plan.newYear} 确认`" class="confirm" />
        <el-button type="danger" :loading="rolling" :disabled="confirmYear !== plan.newYear"
          @click="doRoll">执行滚动</el-button>
      </div>
      <el-result v-if="done" icon="success" title="学年滚动完成"
        :sub-title="`已滚动到 ${done.newSchoolYear}：晋升 ${done.promotedClasses} 班、毕业 ${done.graduatedStudents} 人、新建 ${done.newGrades} 个年级与 ${done.newTerms} 个学期。`" />
    </template>
    <el-empty v-else-if="!loading" description="点击上方按钮预览滚动方案" :image-size="80" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import { api } from '../../api/http'

const loading = ref(false)
const rolling = ref(false)
const plan = ref<any>(null)
const confirmYear = ref('')
const done = ref<any>(null)

async function load() {
  loading.value = true
  try {
    plan.value = await api<any>('/api/admin/school-year/preview')
    confirmYear.value = ''
    done.value = null
  } finally {
    loading.value = false
  }
}

async function doRoll() {
  await ElMessageBox.confirm(
    `确认执行学年滚动 ${plan.value.oldYear} → ${plan.value.newYear}？毕业年级在读学生将全部标记为「毕业」，此操作不可自动撤销（执行前请确认已完成毕业班文件归档导出）。`,
    '最后确认',
    { type: 'warning', confirmButtonText: '执行滚动', cancelButtonText: '再想想' },
  )
  rolling.value = true
  try {
    done.value = await api<any>('/api/admin/school-year/roll', {
      method: 'POST',
      json: { confirmNewYear: confirmYear.value },
    })
    ElMessage.success('学年滚动完成')
  } finally {
    rolling.value = false
  }
}
</script>

<style scoped>
.tip { margin-bottom: 12px; }
.toolbar { margin-bottom: 12px; }
.year-line { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.year-line .arrow { color: #909399; }
.year-line .stat { font-size: 13px; color: #606266; }
.sec { margin: 16px 0 8px; font-weight: 600; font-size: 14px; }
.no-term { font-weight: 400; color: #e6a23c; font-size: 13px; }
.warn { margin-top: 8px; }
.roll-row { display: flex; gap: 10px; }
.confirm { max-width: 260px; }
</style>
