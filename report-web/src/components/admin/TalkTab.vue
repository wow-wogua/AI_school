<template>
  <div>
    <h4>导师谈心全量（教师端「导师谈心」提交；家长不可见）</h4>
    <div class="bar">
      <el-select v-model="qClass" clearable filterable placeholder="全部班级" style="width: 180px" @change="load">
        <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-checkbox v-model="qFollow" @change="load">只看待随访</el-checkbox>
    </div>
    <el-table :data="rows" size="small">
      <el-table-column prop="studentName" label="学生" width="90" />
      <el-table-column prop="className" label="班级" width="120">
        <template #default="{ row }">{{ row.className || '—' }}</template>
      </el-table-column>
      <el-table-column prop="teacherName" label="记录教师" width="100" />
      <el-table-column label="类型" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="tagType(row.talkType)">{{ row.talkType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="talkDate" label="谈心日期" width="110" />
      <el-table-column label="随访" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.followUp === 1" type="warning" size="small">待随访 {{ row.followDue }}</el-tag>
          <el-tag v-else-if="row.followUp === 2" type="success" size="small">已随访</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
      <el-table-column label="记录时间" width="140">
        <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api/http'

const qClass = ref<number | ''>('')
const qFollow = ref(false)
const classes = ref<any[]>([])
const rows = ref<any[]>([])

async function load() {
  const p = new URLSearchParams()
  if (qClass.value) p.set('classId', String(qClass.value))
  if (qFollow.value) p.set('followUp', '1')
  rows.value = await api<any[]>(`/api/admin/talk/list?${p}`)
}

function fmtTime(t?: string) {
  return t ? t.slice(0, 16).replace('T', ' ') : '—'
}
function tagType(t: string) {
  return ({ 学业: 'primary', 心理: 'warning', 纪律: 'danger', 生活: 'success' } as Record<string, string>)[t] || 'info'
}

onMounted(async () => {
  classes.value = await api<any[]>('/api/admin/class/list').catch(() => [])
  await load()
})
</script>

<style scoped>
.bar { display: flex; align-items: center; gap: 10px; margin: 10px 0 8px; }
</style>
