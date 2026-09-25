<template>
  <div class="page">
    <motion.h2 class="page-title" :initial="{ opacity: 0, x: -16 }" :animate="{ opacity: 1, x: 0 }"
      :transition="{ type: 'spring', stiffness: 400, damping: 32 }"><el-icon><Aim /></el-icon>综合素质</motion.h2>
    <div class="toolbar">
      <el-select v-model="classId" placeholder="班级" style="min-width: 140px" @change="loadStudents">
        <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="studentIds" multiple collapse-tags collapse-tags-tooltip filterable
        placeholder="选择学生（可多选批量）" style="min-width: 200px" @change="onStudentChange">
        <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-select v-model="termId" placeholder="学期" style="min-width: 160px" @change="load">
        <el-option v-for="t in terms" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
    </div>

    <el-card v-if="studentIds.length">
      <template #header>
        综合素质评价（五维 A–D；final 由系统按「众数并列取高」自动裁定，保存后进入报告单）
        <el-tag v-if="studentIds.length > 1" type="warning" size="small" style="margin-left: 8px">
          已选 {{ studentIds.length }} 名学生，将为每人写入相同五维等级
        </el-tag>
      </template>
      <el-form label-width="90px" style="max-width: 560px">
        <el-form-item v-for="d in dims" :key="d.key" :label="d.label">
          <el-select v-model="d.value" clearable style="width: 140px">
            <el-option v-for="lv in ['A', 'B', 'C', 'D']" :key="lv" :label="lv" :value="lv" />
          </el-select>
        </el-form-item>
        <el-form-item label="综合等级">
          <el-tag v-if="finalPreview" size="large">{{ finalPreview }}</el-tag>
          <span v-else class="hint">至少评一维后自动计算</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" :disabled="!finalPreview" @click="save">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { motion } from 'motion-v'
import { ElMessage } from 'element-plus'
import { api } from '../api/http'

const route = useRoute()

const classes = ref<{ id: number; name: string }[]>([])
const terms = ref<any[]>([])
const students = ref<{ id: number; name: string }[]>([])
const classId = ref<number>()
const termId = ref<number>()
/** 学生多选（批15）：单选=回显该生五维；多选=清空维度后统一评（防把上个学生的值误写给他人） */
const studentIds = ref<number[]>([])

/** 切学生先清五维（请求失败/晚到时上个学生的等级不残留，防误写给新选的学生）；单选再回显 */
function onStudentChange() {
  dims.value.forEach((d) => (d.value = ''))
  if (studentIds.value.length === 1) {
    load()
  }
}
const saving = ref(false)
const dims = ref([
  { key: 'moral', label: '思想品德', value: '' },
  { key: 'ability', label: '学业水平', value: '' },
  { key: 'health', label: '身心健康', value: '' },
  { key: 'aesthetic', label: '艺术素养', value: '' },
  { key: 'practice', label: '社会实践', value: '' },
])

const finalPreview = computed(() => {
  // 与服务端同口径：众数并列取高（A>B>C>D）
  const count: Record<string, number> = {}
  dims.value.forEach((d) => d.value && (count[d.value] = (count[d.value] ?? 0) + 1))
  let best = ''
  let n = 0
  for (const lv of ['A', 'B', 'C', 'D']) {
    if ((count[lv] ?? 0) > n) {
      best = lv
      n = count[lv]
    }
  }
  return best
})

async function init() {
  const [cs, ts] = await Promise.all([
    api<{ id: number; name: string }[]>('/api/meta/my-classes'),
    api<any[]>('/api/meta/terms'),
  ])
  classes.value = cs
  terms.value = ts
  termId.value = ts.find((t: any) => t.isCurrent === 1)?.id ?? ts[0]?.id
  if (cs.length) {
    classId.value = cs[0].id
    await loadStudents()
  }
  await preselect()
}

/** 学生详情宫格带学生进来：自动选中该生（班级 → 学生 → 拉五维） */
async function preselect() {
  const sid = Number(route.query.studentId)
  if (!sid) return
  if (route.query.termId && terms.value.some((t: any) => t.id === Number(route.query.termId))) {
    termId.value = Number(route.query.termId)
  }
  try {
    const s = await api<{ classId?: number }>(`/api/student/${sid}`)
    if (s.classId && classes.value.some((c) => c.id === s.classId)) {
      classId.value = s.classId
      await loadStudents()
      studentIds.value = [sid]
      await load()
    }
  } catch { /* 深链失效则保持默认视图 */ }
}

async function loadStudents() {
  if (!classId.value) return
  const d = await api<{ records: { id: number; name: string }[] }>(
    `/api/student/list?classId=${classId.value}&page=1&size=100`)
  students.value = d.records
  studentIds.value = []
  dims.value.forEach((d) => (d.value = ''))
}

let loadSeq = 0
async function load() {
  if (studentIds.value.length !== 1 || !termId.value) return
  const my = ++loadSeq
  const c = await api<Record<string, string>>(
    `/api/comprehensive?studentId=${studentIds.value[0]}&termId=${termId.value}`)
  if (my !== loadSeq) return // 已切走，丢弃晚到的旧响应
  dims.value.forEach((d) => (d.value = c[d.key] ?? ''))
}

/** 保存：单选=现状；多选=同组五维逐生写入 */
async function save() {
  if (!studentIds.value.length || !termId.value) return
  saving.value = true
  try {
    let ok = 0, failed = 0, finalLevel = ''
    for (const sid of studentIds.value) {
      try {
        const body: Record<string, unknown> = { studentId: sid, termId: termId.value }
        dims.value.forEach((d) => (body[d.key] = d.value || ''))
        const r = await api<{ finalLevel: string }>('/api/comprehensive', { method: 'PUT', json: body })
        ok++
        finalLevel = r.finalLevel
      } catch { failed++ }
    }
    if (failed) ElMessage.warning(`成功 ${ok} 人，失败 ${failed} 人`)
    else ElMessage.success(`已保存 ${ok} 人，综合等级 ${finalLevel}`)
  } finally {
    saving.value = false
  }
}

onMounted(init)
</script>

<style scoped>
.hint { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
