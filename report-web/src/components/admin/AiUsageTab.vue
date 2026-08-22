<template>
  <div>
    <div class="toolbar">
      <el-radio-group v-model="days" @change="load">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
        <el-radio-button :value="90">近 90 天</el-radio-button>
      </el-radio-group>
      <span class="hint">统计走大模型的成功任务（模板降级不计）；1 千 tokens ≈ 1~2 分钱（以供应商定价为准）</span>
    </div>

    <h4 class="sec">按日用量</h4>
    <el-table :data="byDay" size="small" max-height="320">
      <el-table-column prop="day" label="日期" width="130" />
      <el-table-column prop="tasks" label="任务数" width="100" />
      <el-table-column label="输入 tokens" width="140">
        <template #default="{ row }">{{ fmt(row.promptTokens) }}</template>
      </el-table-column>
      <el-table-column label="输出 tokens" min-width="140">
        <template #default="{ row }">{{ fmt(row.completionTokens) }}</template>
      </el-table-column>
      <template #empty>该时段暂无大模型调用</template>
    </el-table>

    <h4 class="sec">按教师用量</h4>
    <el-table :data="byTeacher" size="small" max-height="320">
      <el-table-column prop="teacher" label="教师" width="160" />
      <el-table-column prop="tasks" label="任务数" width="100" />
      <el-table-column label="输入 tokens" width="140">
        <template #default="{ row }">{{ fmt(row.promptTokens) }}</template>
      </el-table-column>
      <el-table-column label="输出 tokens" min-width="140">
        <template #default="{ row }">{{ fmt(row.completionTokens) }}</template>
      </el-table-column>
      <template #empty>该时段暂无大模型调用</template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api/http'

const days = ref(30)
const byDay = ref<any[]>([])
const byTeacher = ref<any[]>([])

function fmt(v?: number) {
  return Number(v ?? 0).toLocaleString()
}

async function load() {
  const d = await api<{ byDay: any[]; byTeacher: any[] }>(`/api/admin/ai/usage?days=${days.value}`)
  byDay.value = d.byDay
  byTeacher.value = d.byTeacher
}

onMounted(load)
</script>

<style scoped>
.hint { font-size: 12px; color: var(--el-text-color-secondary); }
.sec { margin: 18px 0 8px; font-size: 14px; font-weight: 600; }
</style>
