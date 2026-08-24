<template>
  <div class="page">
    <motion.h2 class="page-title" :initial="{ opacity: 0, x: -16 }" :animate="{ opacity: 1, x: 0 }"
      :transition="{ type: 'spring', stiffness: 400, damping: 32 }"><el-icon><Trophy /></el-icon>荣誉证书</motion.h2>
    <div class="toolbar">
      <el-select v-model="classId" placeholder="班级" style="min-width: 140px" @change="loadStudents">
        <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="studentId" filterable placeholder="选择学生" style="min-width: 160px" @change="load">
        <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-upload
        :show-file-list="false"
        accept=".jpg,.jpeg,.png,.pdf"
        :disabled="!studentId"
        :http-request="upload"
      >
        <el-button type="primary" :disabled="!studentId" :loading="uploading">上传证书</el-button>
      </el-upload>
      <span class="hint">支持 jpg/jpeg/png/pdf，单个不超过 10MB；图片可 AI 识别，PDF 需手动填写</span>
    </div>

    <el-alert v-if="uploadInfo" :title="uploadInfo" type="info" :closable="true" style="margin-bottom: 12px" />

    <el-table :data="honors" v-loading="loading">
      <el-table-column prop="name" label="奖项名称" min-width="150" />
      <el-table-column prop="level" label="级别" width="80" />
      <el-table-column prop="issuer" label="主办单位" width="120" />
      <el-table-column prop="honorDate" label="日期" width="105" />
      <el-table-column label="状态" width="85">
        <template #default="{ row }">
          <el-tag :type="row.confirmStatus === '已确认' ? 'success' : 'warning'" size="small">{{ row.confirmStatus }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link size="small" @click="view(row)">查看证书</el-button>
          <template v-if="row.confirmStatus === '待确认'">
            <el-button link size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link size="small" @click="openConfirm(row)">确认生效</el-button>
            <el-button link size="small" @click="del(row)">删除</el-button>
          </template>
        </template>
      </el-table-column>
      <template #empty>{{ studentId ? '暂无荣誉记录，点「上传证书」新增' : '请先选择学生' }}</template>
    </el-table>

    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑荣誉' : '荣誉信息'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="奖项名称" required><el-input v-model="editForm.name" /></el-form-item>
        <el-form-item label="级别">
          <el-select v-model="editForm.level" clearable style="width: 100%">
            <el-option v-for="l in levels" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="主办单位"><el-input v-model="editForm.issuer" /></el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="editForm.honorDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item v-if="confirming" label="能量币">
          <el-input-number v-model="editForm.coin" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button v-if="!confirming" type="primary" :disabled="!editForm.name" @click="save">保存</el-button>
        <el-button v-else type="primary" :disabled="!editForm.name" @click="confirm">确认生效</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { motion } from 'motion-v'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { api, apiForm, fetchBlob } from '../api/http'

interface Honor { id: number; name: string; level?: string; issuer?: string; honorDate?: string; fileUrl?: string; confirmStatus: string }

const levels = ['国家级', '省级', '市级', '区级', '校级', '班级']
const classes = ref<{ id: number; name: string }[]>([])
const students = ref<{ id: number; name: string }[]>([])
const classId = ref<number>()
const studentId = ref<number>()
const honors = ref<Honor[]>([])
const loading = ref(false)
const uploading = ref(false)
const uploadInfo = ref('')
const editVisible = ref(false)
const confirming = ref(false)
const editForm = ref<Partial<Honor> & { coin?: number }>({})

async function init() {
  classes.value = await api('/api/meta/my-classes')
  if (classes.value.length) {
    classId.value = classes.value[0].id
    await loadStudents()
  }
}

async function loadStudents() {
  if (!classId.value) return
  const d = await api<{ records: { id: number; name: string }[] }>(
    `/api/student/list?classId=${classId.value}&page=1&size=100`,
  )
  students.value = d.records
  studentId.value = undefined
  honors.value = []
}

async function load() {
  if (!studentId.value) return
  loading.value = true
  try {
    honors.value = await api(`/api/honor/list?studentId=${studentId.value}`)
  } finally {
    loading.value = false
  }
}

async function upload(opt: UploadRequestOptions) {
  if (!studentId.value) return
  uploading.value = true
  uploadInfo.value = ''
  try {
    const form = new FormData()
    form.append('studentId', String(studentId.value))
    form.append('file', opt.file)
    const r = await apiForm<{ honorId: number; source: string; detail: string; parsed: { name?: string; level?: string; issuer?: string; date?: string } | null }>(
      '/api/honor/upload', form,
    )
    uploadInfo.value = `上传成功（${r.source === 'ai' ? 'AI 已识别，请核对' : '请手动填写'}：${r.detail}）`
    openEdit({ id: r.honorId, name: r.parsed?.name || '', level: r.parsed?.level, issuer: r.parsed?.issuer, honorDate: r.parsed?.date, confirmStatus: '待确认' })
    await load()
  } finally {
    uploading.value = false
  }
}

function openEdit(h: Honor) {
  confirming.value = false
  editForm.value = { ...h, coin: 0 }
  editVisible.value = true
}

function openConfirm(h: Honor) {
  confirming.value = true
  editForm.value = { ...h, coin: 0 }
  editVisible.value = true
}

async function save() {
  const f = editForm.value
  await api(`/api/honor/${f.id}`, {
    method: 'PUT',
    json: { name: f.name, level: f.level, issuer: f.issuer, honorDate: f.honorDate },
  })
  ElMessage.success('已保存')
  editVisible.value = false
  await load()
}

async function confirm() {
  const f = editForm.value
  await api(`/api/honor/${f.id}`, {
    method: 'PUT',
    json: { name: f.name, level: f.level, issuer: f.issuer, honorDate: f.honorDate },
  })
  const r = await api<{ termId: number | null }>(`/api/honor/${f.id}/confirm`, {
    method: 'PUT',
    json: { coin: f.coin || undefined },
  })
  ElMessage.success(r.termId ? `已确认生效，能量币已入账（学期 #${r.termId}）` : '已确认生效')
  editVisible.value = false
  await load()
}

async function del(h: Honor) {
  await ElMessageBox.confirm(`删除荣誉「${h.name || '未命名'}」？`, '提示', { type: 'warning' })
  await api(`/api/honor/${h.id}`, { method: 'DELETE' })
  ElMessage.success('已删除')
  await load()
}

async function view(h: Honor) {
  const blob = await fetchBlob(`/api/honor/file/${h.id}`)
  window.open(URL.createObjectURL(blob))
}

onMounted(init)
</script>

<style scoped>
.hint { color: #909399; font-size: 12px; }
</style>
