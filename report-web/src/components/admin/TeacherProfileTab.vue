<template>
  <div>
    <div class="tip">全校教师档案总览。老师未填的（标「未填」）可在此代填，老师本人仍可在 App「我的 → 教师档案」维护自己的。</div>
    <el-table :data="rows" size="small">
      <el-table-column label="照片" width="64">
        <template #default="{ row }">
          <img v-if="photos[row.userId]" class="pfp" :src="photos[row.userId]" alt="照片">
          <div v-else class="pfp pfp-none">{{ row.realName?.charAt(0) }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="realName" label="姓名" width="100" />
      <el-table-column label="角色" width="90">
        <template #default="{ row }">{{ roleName(row.role) }}</template>
      </el-table-column>
      <el-table-column label="任教学科" width="100">
        <template #default="{ row }">{{ row.subjectName ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="职称" width="100">
        <template #default="{ row }">{{ row.title ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="工号" width="100">
        <template #default="{ row }">{{ row.employeeNo ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="phone" label="手机" width="130" />
      <el-table-column label="档案" width="80">
        <template #default="{ row }">
          <el-tag :type="row.hasProfile ? 'success' : 'warning'" size="small">{{ row.hasProfile ? '已填' : '未填' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="70">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="`编辑 ${editing?.realName ?? ''} 的教师档案`" width="460px">
      <el-form label-width="90px">
        <el-form-item label="照片">
          <div class="photo-row">
            <img v-if="editPhoto" class="pfp-lg" :src="editPhoto" alt="照片">
            <div v-else class="pfp-lg pfp-none">{{ editing?.realName?.charAt(0) }}</div>
            <input type="file" accept="image/jpeg,image/png" @change="onPickPhoto" />
            <div v-if="photoFile" class="photo-hint">已选新照片，保存时上传</div>
          </div>
        </el-form-item>
        <el-form-item label="工号">
          <el-input v-model="form.employeeNo" placeholder="全校唯一" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="form.gender" clearable style="width: 100%">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
        </el-form-item>
        <el-form-item label="任教学科">
          <el-select v-model="form.subjectId" clearable style="width: 100%">
            <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="职称">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="职务">
          <el-input v-model="form.duty" />
        </el-form-item>
        <el-form-item label="教龄">
          <el-input-number v-model="form.teachingYears" :min="0" :max="60" style="width: 140px" />
        </el-form-item>
        <el-form-item label="入职年月">
          <el-date-picker v-model="form.hireMonth" type="month" value-format="YYYY-MM" style="width: 140px" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.intro" type="textarea" :rows="3" maxlength="300" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, apiForm, fetchBlob } from '../../api/http'

const rows = ref<any[]>([])
const subjects = ref<{ id: number; name: string }[]>([])
const photos = ref<Record<number, string>>({})
const dialog = ref(false)
const editing = ref<any>(null)
const form = ref<any>({})
const photoFile = ref<File | null>(null)
const editPhoto = ref('')
const saving = ref(false)

function roleName(r: string) {
  return { ADMIN: '管理员', HEAD_TEACHER: '班主任', TEACHER: '任课教师' }[r] ?? r
}

async function load() {
  rows.value = await api<any[]>('/api/profile/admin/list')
  photos.value = {}
  await Promise.all(rows.value.filter((r) => r.photoUrl).map(async (r) => {
    try {
      const blob = await fetchBlob(r.photoUrl)
      photos.value[r.userId] = URL.createObjectURL(blob)
    } catch { /* 加载失败回退首字 */ }
  }))
}

function openEdit(row: any) {
  editing.value = row
  photoFile.value = null
  editPhoto.value = photos.value[row.userId] ?? ''
  form.value = {
    employeeNo: row.employeeNo ?? '',
    gender: row.gender ?? '',
    subjectId: row.subjectId ?? undefined,
    title: row.title ?? '',
    duty: row.duty ?? '',
    teachingYears: row.teachingYears ?? undefined,
    hireMonth: row.hireDate ? row.hireDate.slice(0, 7) : '',
    intro: row.intro ?? '',
  }
  dialog.value = true
}

function onPickPhoto(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0] ?? null
  if (!f) return
  if (f.size > 5 * 1024 * 1024) {
    ElMessage.warning('照片不能超过 5MB')
    return
  }
  photoFile.value = f
  editPhoto.value = URL.createObjectURL(f)
}

async function save() {
  if (!editing.value) return
  saving.value = true
  try {
    if (photoFile.value) {
      const fd = new FormData()
      fd.append('photo', photoFile.value)
      await apiForm(`/api/profile/admin/${editing.value.userId}/photo`, fd)
    }
    await api(`/api/profile/admin/${editing.value.userId}`, {
      method: 'PUT',
      json: {
        employeeNo: form.value.employeeNo,
        gender: form.value.gender,
        subjectId: form.value.subjectId,
        title: form.value.title,
        duty: form.value.duty,
        teachingYears: form.value.teachingYears,
        hireDate: form.value.hireMonth ? form.value.hireMonth + '-01' : null,
        intro: form.value.intro,
      },
    })
    ElMessage.success('档案已保存')
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await load()
  subjects.value = await api<{ id: number; name: string }[]>('/api/meta/subjects')
})
</script>

<style scoped>
.tip { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 12px; }
.pfp { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; }
.pfp-lg { width: 64px; height: 64px; border-radius: 50%; object-fit: cover; }
.pfp-none { display: flex; align-items: center; justify-content: center;
  background: #f0f2f8; color: #909399; font-weight: 600; }
.pfp-lg.pfp-none { font-size: 24px; }
.photo-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.photo-hint { font-size: 12px; color: var(--el-color-success); }
</style>
