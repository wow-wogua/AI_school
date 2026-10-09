<template>
  <div>
    <h4>文明班评比：打分流水（历史）+ 班级记分流水 + 自动排名 + 月度评选；细则见教师端「德育规范」页</h4>

    <!-- 打分流水（批39⑥ 已下线，近 200 条历史，误录可删） -->
    <div class="bar">
      <el-select v-model="qClass" clearable filterable placeholder="全部班级" style="width: 180px" @change="loadRecords">
        <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-date-picker v-model="qDate" type="date" placeholder="按日期" clearable style="width: 160px"
        value-format="YYYY-MM-DD" @change="loadRecords" />
    </div>
    <el-table :data="records" size="small">
      <el-table-column prop="scoreDate" label="日期" width="110" />
      <el-table-column prop="className" label="班级" width="120" />
      <el-table-column label="大项" width="60">
        <template #default="{ row }">{{ row.sectionNo }}</template>
      </el-table-column>
      <el-table-column prop="itemText" label="条目" min-width="220" show-overflow-tooltip />
      <el-table-column label="分值" width="90">
        <template #default="{ row }">
          <b :class="Number(row.delta) >= 0 ? 'pos' : 'neg'">{{ Number(row.delta) >= 0 ? '+' : '' }}{{ row.delta }}×{{ row.cnt }}</b>
        </template>
      </el-table-column>
      <el-table-column prop="note" label="备注" width="140" show-overflow-tooltip />
      <el-table-column prop="operatorName" label="打分教师" width="100" />
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button size="small" text type="danger" @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 班级记分流水（批43：现行班级整体加减分，误录可删） -->
    <h4 style="margin-top: 22px">班级记分流水（批43：班主任/级长/学成中心/管理员在「素养评价 → 班级记分」录入）</h4>
    <div class="bar">
      <el-select v-model="csQClass" clearable filterable placeholder="选择班级" style="width: 180px" @change="loadCsRecords">
        <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
    </div>
    <el-table :data="csRecords" size="small">
      <el-table-column prop="scoreDate" label="日期" width="110" />
      <el-table-column label="大项" width="110">
        <template #default="{ row }">{{ row.category ? conductSections[row.category - 1]?.name ?? row.category : '—' }}</template>
      </el-table-column>
      <el-table-column prop="itemText" label="事项" min-width="220" show-overflow-tooltip />
      <el-table-column label="分值" width="70">
        <template #default="{ row }">
          <b :class="Number(row.delta) >= 0 ? 'pos' : 'neg'">{{ Number(row.delta) >= 0 ? '+' : '' }}{{ row.delta }}</b>
        </template>
      </el-table-column>
      <el-table-column prop="note" label="备注" width="140" show-overflow-tooltip />
      <el-table-column prop="operatorName" label="记分人" width="100" />
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button size="small" text type="danger" @click="delCs(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 自动排名（区间聚合） -->
    <h4 style="margin-top: 22px">区间排名（自动汇总：检查日×120 + 素养评价分 + 班级记分）</h4>
    <div class="bar">
      <el-date-picker v-model="rFrom" type="date" style="width: 160px" value-format="YYYY-MM-DD" @change="loadRank" />
      <span>至</span>
      <el-date-picker v-model="rTo" type="date" style="width: 160px" value-format="YYYY-MM-DD" @change="loadRank" />
      <el-select v-model="rGrade" clearable placeholder="全部年级" style="width: 150px" @change="loadRank">
        <el-option v-for="g in grades" :key="g.id" :label="g.name" :value="g.id" />
      </el-select>
    </div>
    <el-table :data="rankRows" size="small">
      <el-table-column prop="gradeName" label="年级" width="110" />
      <el-table-column label="名次" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.rankNo <= 3" size="small" type="warning">{{ ['金', '银', '铜'][row.rankNo - 1] }}</el-tag>
          <span v-else>{{ row.rankNo }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="className" label="班级" width="130" />
      <el-table-column prop="totalScore" label="总分" width="110" />
    </el-table>
    <p class="tip">本月检查 {{ rankDays }} 天（有素养评价或班级记分记录的日子；无记录的班级当天不计）</p>

    <!-- 月度评选 -->
    <h4 style="margin-top: 22px">文明班评选（按月冻结快照，重评覆盖；每月 1 日 08:10 自动评选上月）</h4>
    <div class="bar">
      <el-date-picker v-model="settleMonth" type="month" placeholder="选择月份" style="width: 160px"
        value-format="YYYY-MM" :disabled-date="(d: Date) => d.getTime() > Date.now()" />
      <el-button type="primary" size="small" @click="settle">评选该月</el-button>
    </div>
    <el-table :data="awards" size="small">
      <el-table-column prop="periodValue" label="月份" width="100" />
      <el-table-column prop="gradeName" label="年级" width="110" />
      <el-table-column label="名次" width="70">
        <template #default="{ row }">
          <el-tag size="small" type="warning">{{ ['金', '银', '铜'][row.rankNo - 1] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="className" label="文明班" width="130" />
      <el-table-column prop="totalScore" label="总分" width="110" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../api/http'
import { CONDUCT_SECTIONS } from '../../data/conductRules'

const conductSections = CONDUCT_SECTIONS
const classes = ref<any[]>([])
const grades = ref<any[]>([])
const qClass = ref<number | ''>('')
const qDate = ref('')
const records = ref<any[]>([])
const csQClass = ref<number | ''>('')
const csRecords = ref<any[]>([])

const now = new Date()
const fmt = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

const rFrom = ref(fmt(new Date(now.getFullYear(), now.getMonth(), 1)))
const rTo = ref(fmt(now))
const rGrade = ref<number | ''>('')
const rankRows = ref<any[]>([])
const rankDays = ref(0)

const settleMonth = ref(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`)
const awards = ref<any[]>([])

async function loadRecords() {
  const p = new URLSearchParams()
  if (qClass.value) p.set('classId', String(qClass.value))
  if (qDate.value) { p.set('from', qDate.value); p.set('to', qDate.value) }
  records.value = await api<any[]>(`/api/civility/records?${p}`)
}

async function loadRank() {
  const p = new URLSearchParams({ from: rFrom.value, to: rTo.value })
  if (rGrade.value) p.set('gradeId', String(rGrade.value))
  const d = await api<any>(`/api/civility/rank?${p}`)
  rankDays.value = d.checkDays
  rankRows.value = (d.grades ?? []).flatMap((g: any) =>
    g.classes.map((c: any) => ({ ...c, gradeName: g.gradeName, checkDays: g.checkDays })))
}

async function loadAwards() {
  awards.value = await api<any[]>('/api/civility/awards')
}

async function del(row: any) {
  try {
    await ElMessageBox.confirm(`删除 ${row.className}「${row.itemText}」这条打分？（排名随之变化）`, '删除确认')
  } catch { return }
  await api(`/api/civility/score/${row.id}`, { method: 'DELETE' })
  ElMessage.success('已删除')
  await Promise.all([loadRecords(), loadRank(), loadAwards()])
}

/** 批43 班级记分流水（管理员视角按班查、可删） */
async function loadCsRecords() {
  csRecords.value = csQClass.value
    ? await api<any[]>(`/api/civility/class-score/list?classId=${csQClass.value}`).catch(() => [])
    : []
}

async function delCs(row: any) {
  try {
    await ElMessageBox.confirm(`删除班级记分「${row.itemText}」？（排名下次汇总自动对齐）`, '删除确认')
  } catch { return }
  await api(`/api/civility/class-score/${row.id}`, { method: 'DELETE' })
  ElMessage.success('已删除')
  await Promise.all([loadCsRecords(), loadRank(), loadAwards()])
}

async function settle() {
  if (!settleMonth.value) return
  const tops = await api<any[]>('/api/civility/settle', { method: 'POST', json: { month: settleMonth.value } })
  ElMessage.success(`已评选 ${settleMonth.value}：文明班 ${tops.length} 个（各年级前三）`)
  await loadAwards()
}

onMounted(async () => {
  classes.value = await api<any[]>('/api/admin/class/list').catch(() => [])
  grades.value = await api<any[]>('/api/meta/grades').catch(() => [])
  await Promise.all([loadRecords(), loadRank(), loadAwards()])
  if (classes.value.length) {
    csQClass.value = classes.value[0].id
    await loadCsRecords()
  }
})
</script>

<style scoped>
.bar { display: flex; align-items: center; gap: 10px; margin: 10px 0 8px; }
.pos { color: #B07A1C; }
.neg { color: #c4313c; }
.tip { margin: 6px 0 0; font-size: 12px; color: var(--el-text-color-secondary); }
</style>
