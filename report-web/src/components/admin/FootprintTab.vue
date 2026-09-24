<template>
  <div>
    <h4>教师成长足迹（公开课/听课/讲座/读书笔记/工作室；奖项维度聚合教师风采）</h4>
    <div class="sum">
      <div v-for="s in SIX" :key="s.key" class="cell">
        <b>{{ summary[s.key] ?? 0 }}</b><span>{{ s.label }}</span>
      </div>
    </div>
    <div class="bar">
      <el-select v-model="qTeacher" clearable filterable placeholder="全部教师" style="width: 180px" @change="load">
        <el-option v-for="t in teachers" :key="t.id" :label="t.realName || t.username" :value="t.id" />
      </el-select>
      <el-select v-model="qType" clearable placeholder="全部类型" style="width: 140px" @change="load">
        <el-option v-for="s in SIX.filter((x) => x.key !== 'AWARD')" :key="s.key" :label="s.label" :value="s.key" />
      </el-select>
    </div>
    <el-table :data="rows" size="small">
      <el-table-column prop="teacherName" label="教师" width="100" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="tagType(row.type)">{{ labelOf(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="footDate" label="日期" width="110" />
      <el-table-column prop="place" label="地点" width="130">
        <template #default="{ row }">{{ row.place || '—' }}</template>
      </el-table-column>
      <el-table-column prop="note" label="内容" min-width="200" show-overflow-tooltip />
      <el-table-column label="记录时间" width="140">
        <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api/http'

const SIX = [
  { key: 'OPEN_CLASS', label: '公开课' },
  { key: 'OBSERVE', label: '听课' },
  { key: 'AWARD', label: '奖项' },
  { key: 'LECTURE', label: '讲座' },
  { key: 'READING', label: '读书笔记' },
  { key: 'STUDIO', label: '工作室' },
]
const LABEL: Record<string, string> = { OPEN_CLASS: '公开课', OBSERVE: '听课', LECTURE: '讲座', READING: '读书笔记', STUDIO: '工作室' }
const TAG: Record<string, string> = { OPEN_CLASS: 'primary', OBSERVE: 'success', LECTURE: 'warning', READING: 'info', STUDIO: 'danger' }

const qTeacher = ref<number | ''>('')
const qType = ref('')
const teachers = ref<any[]>([])
const rows = ref<any[]>([])
const summary = ref<Record<string, number>>({})

async function load() {
  const p = new URLSearchParams()
  if (qTeacher.value) p.set('teacherId', String(qTeacher.value))
  if (qType.value) p.set('type', qType.value)
  rows.value = await api<any[]>(`/api/admin/footprint/list?${p}`)
}
async function loadSummary() {
  summary.value = await api<Record<string, number>>('/api/admin/footprint/summary').catch(() => ({}))
}

function labelOf(t: string) { return LABEL[t] || t }
function tagType(t: string) { return TAG[t] || 'info' }
function fmtTime(t?: string) {
  return t ? t.slice(0, 16).replace('T', ' ') : '—'
}

onMounted(async () => {
  teachers.value = await api<any[]>('/api/admin/user/list?role=TEACHER&page=1&size=1000')
    .then((d: any) => d.records ?? d).catch(() => [])
  await Promise.all([load(), loadSummary()])
})
</script>

<style scoped>
.sum { display: flex; gap: 10px; margin: 0 0 14px; flex-wrap: wrap; }
.cell { flex: 1; min-width: 88px; background: var(--el-fill-color-light); border-radius: 8px;
  padding: 10px 0; text-align: center; }
.cell b { display: block; font-size: 20px; color: var(--el-color-primary); }
.cell span { font-size: 12px; color: var(--el-text-color-secondary); }
.bar { display: flex; gap: 10px; margin-bottom: 12px; }
</style>
