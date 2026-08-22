<template>
  <div class="page">
    <motion.h2 class="page-title" :initial="{ opacity: 0, x: -16 }" :animate="{ opacity: 1, x: 0 }"
      :transition="{ type: 'spring', stiffness: 400, damping: 32 }"><el-icon><Flag /></el-icon>活动管理</motion.h2>
    <div class="toolbar">
      <el-button v-if="isAdmin" type="primary" @click="openEdit()">新建活动</el-button>
      <el-tag class="stat-chip">共 {{ activities.length }} 个活动</el-tag>
    </div>

    <el-table :data="activities" highlight-current-row @row-click="select">
      <el-table-column label="封面" width="80">
        <template #default="{ row }">
          <el-image v-if="row.coverUrl" :src="covers[row.id]" fit="cover"
            style="width: 56px; height: 40px; border-radius: 4px" :preview-src-list="[covers[row.id]]"
            preview-teleported hide-on-click-modal @click.stop />
          <span v-else style="color: #c0c4cc; font-size: 12px">无</span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="活动名称" min-width="150" />
      <el-table-column prop="type" label="类型" width="90" />
      <el-table-column label="时间" width="150">
        <template #default="{ row }">{{ fmt(row.startTime) }}</template>
      </el-table-column>
      <el-table-column prop="place" label="地点" width="110" />
      <el-table-column v-if="isAdmin" label="操作" width="120">
        <template #default="{ row }">
          <el-button link size="small" @click.stop="openEdit(row)">编辑</el-button>
          <el-button link size="small" @click.stop="del(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>暂无活动，点击上方「新建活动」创建</template>
    </el-table>

    <el-card v-if="current" style="margin-top: 16px">
      <template #header>
        参与记录 — {{ current.title }}
        <el-button type="primary" size="small" style="float: right" @click="openSignup()">录参与</el-button>
      </template>
      <el-table :data="signups">
        <el-table-column prop="studentName" label="学生" width="100" />
        <el-table-column label="签到" width="80">
          <template #default="{ row }">
            <el-tag :type="row.checkinTime ? 'success' : 'info'" size="small">
              {{ row.checkinTime ? '已签到' : '未签到' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="award" label="奖项" width="120" />
        <el-table-column prop="performance" label="表现" min-width="140" />
        <el-table-column label="操作" width="70">
          <template #default="{ row }">
            <el-button link size="small" @click="openSignup(row)">编辑</el-button>
          </template>
        </el-table-column>
        <template #empty>尚无参与记录</template>
      </el-table>
    </el-card>

    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑活动' : '新建活动'" width="480px">
      <el-form label-width="70px">
        <el-form-item label="名称" required><el-input v-model="editForm.title" /></el-form-item>
        <el-form-item label="类型"><el-input v-model="editForm.type" /></el-form-item>
        <el-form-item label="时间">
          <el-date-picker v-model="editForm.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item label="地点"><el-input v-model="editForm.place" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="editForm.intro" type="textarea" :rows="2" /></el-form-item>
        <el-form-item v-if="editForm.id" label="封面">
          <div style="display: flex; align-items: center; gap: 10px">
            <el-image v-if="covers[editForm.id]" :src="covers[editForm.id]" fit="cover"
              style="width: 84px; height: 56px; border-radius: 4px" :preview-src-list="[covers[editForm.id]]"
              preview-teleported />
            <input type="file" accept="image/jpeg,image/png" @change="(e: Event) => (coverFile = (e.target as HTMLInputElement).files?.[0] ?? null)" />
          </div>
          <div class="hint">jpg/png ≤5MB，保存时上传；重复上传覆盖</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!editForm.title" @click="saveActivity">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="signupVisible" :title="signupForm.signupId ? '编辑参与' : '录参与'" width="480px">
      <el-form label-width="70px">
        <template v-if="!signupForm.signupId">
          <el-form-item label="班级">
            <el-select v-model="signupClassId" style="width: 100%" @change="loadStudents">
              <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="学生" required>
            <el-select v-model="signupForm.studentId" filterable style="width: 100%">
              <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item label="签到"><el-switch v-model="signupForm.checkin" /></el-form-item>
        <el-form-item label="奖项"><el-input v-model="signupForm.award" placeholder="如：一等奖（附能量币必填）" /></el-form-item>
        <el-form-item label="表现"><el-input v-model="signupForm.performance" /></el-form-item>
        <el-form-item v-if="!signupForm.signupId" label="能量币">
          <el-input-number v-model="signupForm.coin" :min="0" :disabled="!signupForm.award" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="signupVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!signupForm.signupId && !signupForm.studentId" @click="saveSignup">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { motion } from 'motion-v'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, apiForm, fetchBlob } from '../api/http'
import { useAuthStore } from '../stores/auth'

interface Activity { id: number; title: string; type?: string; startTime?: string; place?: string; intro?: string; coverUrl?: string }
interface Signup { signupId: number; studentId: number; studentName: string; checkinTime?: string; award?: string; performance?: string }

const auth = useAuthStore()
const isAdmin = auth.role === 'ADMIN'

const activities = ref<Activity[]>([])
const current = ref<Activity>()
const signups = ref<Signup[]>([])
const classes = ref<{ id: number; name: string }[]>([])
const students = ref<{ id: number; name: string }[]>([])
const covers = ref<Record<number, string>>({})
const coverFile = ref<File | null>(null)

const editVisible = ref(false)
const editForm = ref<Partial<Activity>>({})
const signupVisible = ref(false)
const signupClassId = ref<number>()
const signupForm = ref<{ signupId?: number; studentId?: number; checkin: boolean; award: string; performance: string; coin: number }>(
  { checkin: false, award: '', performance: '', coin: 0 },
)

function fmt(s?: string) {
  return (s || '').replace('T', ' ').slice(0, 16)
}

async function loadActivities() {
  activities.value = await api('/api/activity/list')
  await loadCovers()
  if (current.value) {
    current.value = activities.value.find((a) => a.id === current.value!.id)
    await loadSignups()
  }
}

/** 封面走带 JWT 的二进制接口，转 object URL 供缩略图/预览 */
async function loadCovers() {
  for (const a of activities.value) {
    if (a.coverUrl && !covers.value[a.id]) {
      try {
        const blob = await fetchBlob(`/api/activity/${a.id}/cover`)
        covers.value[a.id] = URL.createObjectURL(blob)
      } catch {
        /* 已删除的封面对象忽略 */
      }
    }
  }
}

async function select(row: Activity) {
  current.value = row
  await loadSignups()
}

async function loadSignups() {
  if (!current.value) return
  signups.value = await api(`/api/activity/${current.value.id}/signups`)
}

function openEdit(a?: Activity) {
  editForm.value = a ? { ...a } : {}
  coverFile.value = null
  editVisible.value = true
}

async function saveActivity() {
  const f = editForm.value
  if (f.id) {
    await api(`/api/activity/${f.id}`, { method: 'PUT', json: f })
    if (coverFile.value) {
      const form = new FormData()
      form.append('file', coverFile.value)
      const r = await apiForm<{ coverUrl: string }>(`/api/activity/${f.id}/cover`, form)
      ElMessage.success(`已保存，封面上传 ${r.coverUrl}`)
    } else {
      ElMessage.success('已保存')
    }
  } else {
    await api('/api/activity', { method: 'POST', json: f })
    ElMessage.success('已保存（封面请编辑时上传）')
  }
  editVisible.value = false
  await loadActivities()
}

async function del(a: Activity) {
  await ElMessageBox.confirm(`删除活动「${a.title}」？`, '提示', { type: 'warning' })
  await api(`/api/activity/${a.id}`, { method: 'DELETE' })
  ElMessage.success('已删除')
  if (current.value?.id === a.id) current.value = undefined
  await loadActivities()
}

async function openSignup(s?: Signup) {
  signupForm.value = s
    ? { signupId: s.signupId, checkin: !!s.checkinTime, award: s.award || '', performance: s.performance || '', coin: 0 }
    : { checkin: false, award: '', performance: '', coin: 0 }
  if (!s && !classes.value.length) {
    classes.value = await api('/api/meta/my-classes')
    signupClassId.value = classes.value[0]?.id
    await loadStudents()
  }
  signupVisible.value = true
}

async function loadStudents() {
  if (!signupClassId.value) return
  const d = await api<{ records: { id: number; name: string }[] }>(
    `/api/student/list?classId=${signupClassId.value}&page=1&size=100`,
  )
  students.value = d.records
}

async function saveSignup() {
  if (!current.value) return
  const f = signupForm.value
  if (f.signupId) {
    await api(`/api/activity/${current.value.id}/signup/${f.signupId}`, {
      method: 'PUT',
      json: { award: f.award, performance: f.performance, checkin: f.checkin },
    })
  } else {
    const r = await api<{ termId: number | null }>(`/api/activity/${current.value.id}/signup`, {
      method: 'POST',
      json: {
        studentId: f.studentId, checkin: f.checkin, award: f.award,
        performance: f.performance, coin: f.coin || undefined,
      },
    })
    if (r.termId) ElMessage.success(`已记录，能量币已入账（学期 #${r.termId}）`)
  }
  signupVisible.value = false
  await loadSignups()
}

onMounted(loadActivities)
</script>

<style scoped>
.hint { color: #909399; font-size: 12px; }
</style>
