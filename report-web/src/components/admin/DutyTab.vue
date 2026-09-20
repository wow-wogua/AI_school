<template>
  <div>
    <h4>值班排班（开关开启后，日常评价仅「当日值班教师」可操作；管理员/领导不受限。先排班再开开关，避免空排班锁死全员）</h4>
    <div class="bar">
      <span class="lbl">值班校验</span>
      <el-switch :model-value="dutyCheck" :loading="cfgSaving" inline-prompt active-text="开" inactive-text="关"
        @change="(v: any) => setCfg(v)" />
      <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" style="width: 150px" @change="load" />
      <el-select v-model="teacherId" filterable placeholder="选择教师" style="width: 200px">
        <el-option v-for="t in teachers" :key="t.id" :label="t.realName" :value="t.id" />
      </el-select>
      <el-input v-model="note" placeholder="备注（选填）" style="width: 180px" />
      <el-button type="primary" :disabled="!teacherId" @click="add">加入当日值班</el-button>
    </div>
    <el-table :data="rows" size="small">
      <el-table-column prop="dutyDate" label="值班日" width="120" />
      <el-table-column prop="teacherName" label="教师" min-width="120" />
      <el-table-column prop="note" label="备注" min-width="160">
        <template #default="{ row }">{{ row.note ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link type="danger" size="small" @click="remove(row)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <p class="hint" style="margin-top: 10px">
      口径=操作发生日：教师在当天值班即可评价（补录历史日期也按当天是否值班判定）。
    </p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/http'

const dutyCheck = ref(false)
const cfgSaving = ref(false)
const date = ref(new Date().toISOString().slice(0, 10))
const teacherId = ref<number>()
const note = ref('')
const teachers = ref<any[]>([])
const rows = ref<any[]>([])

async function load() {
  rows.value = await api<any[]>(`/api/admin/duty/list?date=${date.value}`)
}

async function setCfg(open: boolean) {
  cfgSaving.value = true
  try {
    await api('/api/admin/duty/config', { method: 'PUT', json: { dutyCheck: open } })
    dutyCheck.value = open
    ElMessage.success(open ? '已开启：日常评价仅当日值班教师可操作' : '已关闭：全员可评价')
  } finally {
    cfgSaving.value = false
  }
}

async function add() {
  if (!teacherId.value) return
  await api('/api/admin/duty', { method: 'POST', json: { dutyDate: date.value, teacherId: teacherId.value, note: note.value || null } })
  ElMessage.success('已加入值班名单')
  note.value = ''
  await load()
}

async function remove(row: any) {
  await api(`/api/admin/duty/${row.id}`, { method: 'DELETE' })
  ElMessage.success('已移除')
  await load()
}

onMounted(async () => {
  const cfg = await api<{ dutyCheck: boolean }>('/api/admin/duty/config')
  dutyCheck.value = !!cfg.dutyCheck
  const d = await api<{ records: any[] }>('/api/admin/user/list?page=1&size=200')
  teachers.value = d.records
  await load()
})
</script>

<style scoped>
.bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin: 10px 0; }
.lbl { font-size: 13px; color: #606266; }
</style>
