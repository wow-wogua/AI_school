<template>
  <div>
    <div class="toolbar">
      <el-select v-model="classId" placeholder="全部班级" clearable style="width: 160px" @change="load">
        <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-input v-model="keyword" placeholder="姓名/学号" style="width: 180px" clearable @change="load" />
      <el-button type="primary" @click="openCreate">新建学生</el-button>
    </div>

    <el-table :data="records" size="small">
      <el-table-column prop="studentNo" label="学号" width="110" />
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column label="性别" width="60">
        <template #default="{ row }">{{ row.gender === 'M' ? '男' : row.gender === 'F' ? '女' : '' }}</template>
      </el-table-column>
      <el-table-column label="班级" width="130">
        <template #default="{ row }">{{ className(row.classId) }}</template>
      </el-table-column>
      <el-table-column label="照片" width="70">
        <template #default="{ row }">
          <el-image v-if="photos[row.id]" :src="photos[row.id]" fit="cover"
            style="width: 36px; height: 36px; border-radius: 4px" :preview-src-list="[photos[row.id]]"
            preview-teleported hide-on-click-modal @click.stop />
          <span v-else style="color: #c0c4cc; font-size: 12px">无</span>
        </template>
      </el-table-column>
      <el-table-column prop="guardianName" label="家长" width="90" />
      <el-table-column prop="guardianPhone" label="家长电话" width="130" />
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="total > 20" layout="prev, pager, next" :total="total" :page-size="20"
      :current-page="page" @current-change="(p: number) => { page = p; load() }" style="margin-top: 10px" />

    <el-dialog v-model="dialog" :title="form.id ? '编辑学生' : '新建学生'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="学号"><el-input v-model="form.studentNo" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="性别">
          <el-select v-model="form.gender" style="width: 120px">
            <el-option label="男" value="M" /><el-option label="女" value="F" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级">
          <el-select v-model="form.classId" style="width: 100%">
            <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="入学日期">
          <el-date-picker v-model="form.enrollDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 120px">
            <el-option label="在读" value="在读" /><el-option label="转出" value="转出" />
          </el-select>
        </el-form-item>
        <el-form-item label="家长姓名"><el-input v-model="form.guardianName" /></el-form-item>
        <el-form-item label="家长电话"><el-input v-model="form.guardianPhone" /></el-form-item>
        <el-form-item v-if="form.id" label="照片">
          <div style="display: flex; align-items: center; gap: 10px">
            <el-image v-if="photos[form.id]" :src="photos[form.id]" fit="cover"
              style="width: 56px; height: 56px; border-radius: 4px" :preview-src-list="[photos[form.id]]"
              preview-teleported />
            <input type="file" accept="image/jpeg,image/png" @change="(e: Event) => (photoFile = (e.target as HTMLInputElement).files?.[0] ?? null)" />
          </div>
          <div class="hint">jpg/png ≤5MB，保存时上传；重复上传覆盖</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, apiForm, fetchBlob } from '../../api/http'

const classes = ref<any[]>([])
const records = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const classId = ref<number>()
const keyword = ref('')
const dialog = ref(false)
const form = ref<any>({})
const photos = ref<Record<number, string>>({})
const photoFile = ref<File | null>(null)

function className(id: number) {
  return classes.value.find((c: any) => c.id === id)?.name ?? id
}

async function load() {
  const qs = new URLSearchParams({ page: String(page.value), size: '20' })
  if (classId.value) qs.set('classId', String(classId.value))
  if (keyword.value) qs.set('keyword', keyword.value)
  const d = await api<{ total: number; records: any[] }>(`/api/admin/student/list?${qs}`)
  total.value = d.total
  records.value = d.records
  await loadPhotos()
}

/** 列表内取有照片学生的缩略图（object URL，20 条/页） */
async function loadPhotos() {
  for (const r of records.value) {
    if (r.photoUrl) {
      try {
        const blob = await fetchBlob(`/api/admin/student/${r.id}/photo`)
        photos.value[r.id] = URL.createObjectURL(blob)
      } catch { /* 无照片或未上传，忽略 */ }
    }
  }
}

function openCreate() {
  form.value = { gender: 'M', status: '在读', classId: classId.value ?? classes.value[0]?.id }
  photoFile.value = null
  dialog.value = true
}

function openEdit(row: any) {
  form.value = { ...row }
  photoFile.value = null
  dialog.value = true
}

async function save() {
  if (form.value.id) {
    await api(`/api/admin/student/${form.value.id}`, { method: 'PUT', json: form.value })
    if (photoFile.value) {
      const fd = new FormData()
      fd.append('file', photoFile.value)
      await apiForm(`/api/admin/student/${form.value.id}/photo`, fd)
    }
  } else {
    await api('/api/admin/student', { method: 'POST', json: form.value })
  }
  ElMessage.success('已保存')
  dialog.value = false
  await load()
}

async function remove(row: any) {
  await ElMessageBox.confirm(`删除学生 ${row.name}？已有成长数据的学生会被拒绝`, '确认')
  await api(`/api/admin/student/${row.id}`, { method: 'DELETE' })
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  classes.value = await api<any[]>('/api/admin/class/list')
  await load()
})
</script>
