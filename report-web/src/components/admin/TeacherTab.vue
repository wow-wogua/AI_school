<template>
  <div>
    <div class="toolbar">
      <el-select v-model="roleFilter" placeholder="角色" clearable style="width: 150px" @change="loadUsers">
        <el-option label="管理员" value="ADMIN" />
        <el-option label="班主任" value="HEAD_TEACHER" />
        <el-option label="任课教师" value="TEACHER" />
      </el-select>
      <el-button type="primary" @click="openCreate">新建账号</el-button>
    </div>

    <el-table :data="users" size="small">
      <el-table-column prop="username" label="登录名" width="120" />
      <el-table-column prop="realName" label="姓名" width="110" />
      <el-table-column label="角色" width="100">
        <template #default="{ row }">{{ roleName(row.role) }}</template>
      </el-table-column>
      <el-table-column prop="employeeNo" label="工号" width="100">
        <template #default="{ row }">{{ row.employeeNo ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="subjectName" label="任教学科" width="100">
        <template #default="{ row }">{{ row.subjectName ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="title" label="职称" width="100">
        <template #default="{ row }">{{ row.title ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="phone" label="手机" width="130" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link @click="openProfile(row)">档案</el-button>
          <el-button link @click="resetPwd(row)">重置密码</el-button>
          <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button link type="danger" @click="removeUser(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 教师档案查看（只读；本人可在 App「我的-教师档案」维护） -->
    <el-dialog v-model="profileDlg" :title="`${viewing?.realName ?? ''} 的教师档案`" width="440px">
      <div v-if="viewing" class="pf">
        <img v-if="pfPhoto" class="pf-photo" :src="pfPhoto" alt="教师照片">
        <div v-else class="pf-photo pf-none">{{ viewing.realName?.charAt(0) }}</div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="工号">{{ viewing.employeeNo ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ viewing.gender ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="任教学科">{{ viewing.subjectName ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="职称">{{ viewing.title ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="职务">{{ viewing.duty ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="教龄">{{ viewing.teachingYears != null ? viewing.teachingYears + ' 年' : '—' }}</el-descriptions-item>
          <el-descriptions-item label="入职年月">{{ viewing.hireDate ? viewing.hireDate.slice(0, 7) : '—' }}</el-descriptions-item>
          <el-descriptions-item label="简介">{{ viewing.intro ?? '—' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <h4>任课关系</h4>
    <div class="toolbar">
      <el-select v-model="teach.teacherId" filterable placeholder="教师" style="width: 150px">
        <el-option v-for="u in users" :key="u.id" :label="u.realName" :value="u.id" />
      </el-select>
      <el-select v-model="teach.classId" placeholder="班级" style="width: 150px">
        <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="teach.subjectId" placeholder="学科" style="width: 150px">
        <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-button type="primary" :disabled="!teach.teacherId || !teach.classId || !teach.subjectId" @click="addTeach">添加任课</el-button>
    </div>
    <el-table :data="teaches" size="small">
      <el-table-column prop="teacherName" label="教师" width="110" />
      <el-table-column prop="className" label="班级" width="130" />
      <el-table-column prop="subjectName" label="学科" width="110" />
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link type="danger" @click="removeTeach(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editing ? '编辑账号' : '新建账号'" width="460px">
      <el-form label-width="90px">
        <el-form-item label="登录名">
          <el-input v-model="form.username" :disabled="!!editing" />
        </el-form-item>
        <el-form-item v-if="!editing" label="初始密码">
          <el-input v-model="form.password" show-password />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="班主任" value="HEAD_TEACHER" />
            <el-option label="任课教师" value="TEACHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="form.phone" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, fetchBlob } from '../../api/http'

const users = ref<any[]>([])
const classes = ref<{ id: number; name: string }[]>([])
const subjects = ref<{ id: number; name: string }[]>([])
const teaches = ref<any[]>([])
const roleFilter = ref('')
const dialog = ref(false)
const editing = ref<any>(null)
const form = ref<any>({})
const teach = ref<{ teacherId?: number; classId?: number; subjectId?: number }>({})

function roleName(r: string) {
  return { ADMIN: '管理员', HEAD_TEACHER: '班主任', TEACHER: '任课教师' }[r] ?? r
}

async function loadUsers() {
  const qs = new URLSearchParams({ page: '1', size: '100' })
  if (roleFilter.value) qs.set('role', roleFilter.value)
  const d = await api<{ records: any[] }>(`/api/admin/user/list?${qs}`)
  users.value = d.records
  await loadTeaches()
  // 教师档案列（工号/学科/职称）：按 userId 合并进用户表
  try {
    const pfs = await api<any[]>('/api/profile/admin/list')
    const byUser = new Map(pfs.map((p) => [p.userId, p]))
    users.value = users.value.map((u) => ({ ...u, ...(byUser.get(u.id) ?? {}) }))
  } catch { /* 档案接口失败仅少几列 */ }
}

const profileDlg = ref(false)
const viewing = ref<any>(null)
const pfPhoto = ref('')
async function openProfile(row: any) {
  viewing.value = row
  pfPhoto.value = ''
  profileDlg.value = true
  if (row.photoUrl) {
    try {
      const blob = await fetchBlob(row.photoUrl)
      pfPhoto.value = URL.createObjectURL(blob)
    } catch { /* 照片加载失败显示首字 */ }
  }
}

async function loadTeaches() {
  teaches.value = await api<any[]>(`/api/admin/teach/list${teach.value.teacherId ? '?teacherId=' + teach.value.teacherId : ''}`)
}

function openCreate() {
  editing.value = null
  form.value = { role: 'TEACHER' }
  dialog.value = true
}

function openEdit(row: any) {
  editing.value = row
  form.value = { username: row.username, realName: row.realName, role: row.role, phone: row.phone }
  dialog.value = true
}

async function saveUser() {
  if (editing.value) {
    await api(`/api/admin/user/${editing.value.id}`, { method: 'PUT', json: form.value })
  } else {
    await api('/api/admin/user', { method: 'POST', json: form.value })
  }
  ElMessage.success('已保存')
  dialog.value = false
  await loadUsers()
}

async function resetPwd(row: any) {
  const { value } = await ElMessageBox.prompt(`为 ${row.realName} 设置新密码`, '重置密码', { inputValue: 'aischool123' })
  await api(`/api/admin/user/${row.id}/password`, { method: 'PUT', json: { password: value } })
  ElMessage.success('密码已重置')
}

async function toggleStatus(row: any) {
  await api(`/api/admin/user/${row.id}/status`, { method: 'PUT', json: { status: row.status === 1 ? 0 : 1 } })
  ElMessage.success(row.status === 1 ? '已停用' : '已启用')
  await loadUsers()
}

async function removeUser(row: any) {
  await ElMessageBox.confirm(`删除账号 ${row.realName}？`, '确认')
  await api(`/api/admin/user/${row.id}`, { method: 'DELETE' })
  ElMessage.success('已删除')
  await loadUsers()
}

async function addTeach() {
  await api('/api/admin/teach', { method: 'POST', json: teach.value })
  ElMessage.success('任课关系已添加')
  teach.value = {}
  await loadTeaches()
}

async function removeTeach(row: any) {
  await api(`/api/admin/teach/${row.id}`, { method: 'DELETE' })
  ElMessage.success('已删除')
  await loadTeaches()
}

onMounted(async () => {
  await loadUsers()
  classes.value = await api<{ id: number; name: string }[]>('/api/meta/my-classes')
  subjects.value = await api<{ id: number; name: string }[]>('/api/meta/subjects')
})
</script>

<style scoped>
.pf { display: flex; flex-direction: column; align-items: center; gap: 12px; }
.pf-photo { width: 84px; height: 84px; border-radius: 50%; object-fit: cover; }
.pf-none { display: flex; align-items: center; justify-content: center;
  background: #f0f2f8; color: #909399; font-size: 30px; font-weight: 600; }
.pf :deep(.el-descriptions) { width: 100%; }
</style>
