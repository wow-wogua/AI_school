<template>
  <div class="page">
    <motion.h2 class="page-title" :initial="{ opacity: 0, x: -16 }" :animate="{ opacity: 1, x: 0 }"
      :transition="{ type: 'spring', stiffness: 400, damping: 32 }"><el-icon><ChatDotRound /></el-icon>日常评价</motion.h2>
    <div class="toolbar">
      <el-select v-model="classId" placeholder="班级" style="min-width: 140px" @change="loadStudents">
        <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="studentId" filterable placeholder="选择学生" style="min-width: 160px" @change="loadHistory">
        <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-select v-model="termId" placeholder="学期" style="min-width: 160px" @change="loadHistory">
        <el-option v-for="t in terms" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
    </div>

    <el-card v-if="studentId">
      <template #header>日常评价（一次评价同时写入九维 / 能量币 / 班年级均值，报告即时可见）</template>
      <el-form label-width="90px">
        <el-form-item label="九维">
          <el-select v-model="gridId" style="width: 180px" @change="loadIndicators">
            <el-option v-for="g in grids" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
          <el-select v-if="indicators.length" v-model="indicatorId" placeholder="二级指标"
            style="width: 200px; margin-left: 8px">
            <el-option v-for="i in indicators" :key="i.id" :label="i.name" :value="i.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分值">
          <el-radio-group v-model="score">
            <el-radio-button v-for="v in [1, 2, 5, -1, -2]" :key="v" :value="v" :class="v > 0 ? 'is-pos' : 'is-neg'">{{ v > 0 ? '+' + v : v }}</el-radio-button>
          </el-radio-group>
          <el-input-number v-model="score" :step="1" controls-position="right" style="width: 120px; margin-left: 8px" />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="title" placeholder="如：课堂发言精彩 / 作业未完成" style="max-width: 360px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="remark" placeholder="选填" style="max-width: 360px" />
        </el-form-item>
        <el-form-item label="评价时间">
          <el-date-picker v-model="evalTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 220px" />
          <span class="hint">默认取当前学期内；学期末日请选 00:00 之前</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" :disabled="!indicatorId || !title" @click="submit">提交评价</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="studentId" style="margin-top: 12px">
      <template #header>本学期评价记录（新录入在前）</template>
      <el-table :data="history" size="small" max-height="420">
        <el-table-column prop="evalTime" label="时间" width="160" />
        <el-table-column prop="gridName" label="九维" width="90" />
        <el-table-column prop="indicatorName" label="指标" width="120" />
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column prop="score" label="分值" width="70" />
        <el-table-column prop="teacherName" label="评价人" width="90" />
        <el-table-column prop="remark" label="备注" min-width="120" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { motion } from 'motion-v'
import { ElMessage } from 'element-plus'
import { api } from '../api/http'

const classes = ref<{ id: number; name: string }[]>([])
const terms = ref<any[]>([])
const students = ref<{ id: number; name: string }[]>([])
const grids = ref<{ id: number; name: string }[]>([])
const indicators = ref<{ id: number; name: string }[]>([])
const history = ref<any[]>([])
const classId = ref<number>()
const termId = ref<number>()
const studentId = ref<number>()
const gridId = ref<number>()
const indicatorId = ref<number>()
const score = ref(1)
const title = ref('')
const remark = ref('')
const evalTime = ref('')
const saving = ref(false)

function defaultEvalTime() {
  // 今天在学期内→今天 12:00；否则当前学期末前一天 12:00（学期末日白天在可写窗口外）
  const cur = terms.value.find((t: any) => t.isCurrent === 1) ?? terms.value[0]
  if (!cur) return ''
  const today = new Date().toISOString().slice(0, 10)
  if (today >= cur.startDate && today < cur.endDate) return today + 'T12:00:00'
  const d = new Date(cur.endDate)
  d.setDate(d.getDate() - 1)
  return d.toISOString().slice(0, 10) + 'T12:00:00'
}

async function init() {
  const [cs, ts, gs] = await Promise.all([
    api<{ id: number; name: string }[]>('/api/meta/my-classes'),
    api<any[]>('/api/meta/terms'),
    api<{ id: number; name: string }[]>('/api/meta/grids'),
  ])
  classes.value = cs
  terms.value = ts
  grids.value = gs
  termId.value = ts.find((t: any) => t.isCurrent === 1)?.id ?? ts[0]?.id
  evalTime.value = defaultEvalTime()
  if (cs.length) {
    classId.value = cs[0].id
    await loadStudents()
  }
}

async function loadStudents() {
  if (!classId.value) return
  const d = await api<{ records: { id: number; name: string }[] }>(
    `/api/student/list?classId=${classId.value}&page=1&size=100`)
  students.value = d.records
  studentId.value = undefined
  history.value = []
}

async function loadIndicators() {
  indicatorId.value = undefined
  if (!gridId.value) return
  indicators.value = await api<{ id: number; name: string }[]>(`/api/meta/indicators?gridId=${gridId.value}`)
  if (indicators.value.length) indicatorId.value = indicators.value[0].id
}

async function loadHistory() {
  if (!studentId.value || !termId.value) return
  if (!gridId.value && grids.value.length) {
    gridId.value = grids.value[0].id
    await loadIndicators()
  }
  history.value = (await api<any[]>(`/api/evaluation/list?studentId=${studentId.value}&termId=${termId.value}`))
    .slice().reverse()
}

async function submit() {
  if (!studentId.value || !indicatorId.value) return
  saving.value = true
  try {
    const r = await api<{ termId: number; weekNo: number }>('/api/evaluation', {
      method: 'POST',
      json: {
        studentId: studentId.value,
        indicatorId: indicatorId.value,
        title: title.value,
        score: score.value,
        remark: remark.value || undefined,
        evalTime: evalTime.value,
      },
    })
    ElMessage.success(`已记录（第 ${r.weekNo} 周），报告单即时生效`)
    title.value = ''
    remark.value = ''
    await loadHistory()
  } finally {
    saving.value = false
  }
}

onMounted(init)
</script>

<style scoped>
.hint { color: var(--el-text-color-secondary); font-size: 12px; margin-left: 8px; }
/* 加分=成长绿、扣分=警示红（激活态覆写） */
:deep(.el-radio-button.is-pos.is-active .el-radio-button__inner) {
  background: var(--brand-grow-deep); border-color: var(--brand-grow-deep); box-shadow: -1px 0 0 0 var(--brand-grow-deep);
}
:deep(.el-radio-button.is-neg.is-active .el-radio-button__inner) {
  background: var(--el-color-danger); border-color: var(--el-color-danger); box-shadow: -1px 0 0 0 var(--el-color-danger);
}
</style>
