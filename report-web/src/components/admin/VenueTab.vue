<template>
  <div>
    <h4>场地字典（教师端「行政办公-场地申请」从此处选取；同场地同日仅放行一张申请单，停用场地不可再申请）</h4>
    <div class="bar">
      <el-button type="primary" @click="openEdit()">新增场地</el-button>
    </div>
    <el-table :data="rows" size="small">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="位置说明" min-width="150">
        <template #default="{ row }">{{ row.location || '—' }}</template>
      </el-table-column>
      <el-table-column label="容纳人数" width="90">
        <template #default="{ row }">{{ row.capacity ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '可申请' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.status === 1 ? 'danger' : 'success'" size="small" @click="toggle(row)">
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editDlg" :title="editForm.id ? '编辑场地' : '新增场地'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="editForm.name" placeholder="例如：报告厅" /></el-form-item>
        <el-form-item label="位置说明"><el-input v-model="editForm.location" placeholder="例如：行政楼 3 层" /></el-form-item>
        <el-form-item label="容纳人数"><el-input-number v-model="editForm.capacity" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDlg = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/http'

const rows = ref<any[]>([])
const editDlg = ref(false)
const editForm = ref<{ id?: number; name: string; location: string; capacity: number | null }>(
  { name: '', location: '', capacity: null })

async function load() {
  rows.value = await api<any[]>('/api/admin/venue')
}

function openEdit(row?: any) {
  editForm.value = row
    ? { id: row.id, name: row.name, location: row.location, capacity: row.capacity }
    : { name: '', location: '', capacity: null }
  editDlg.value = true
}

async function save() {
  if (!editForm.value.name.trim()) { ElMessage.warning('请填写名称'); return }
  await api('/api/admin/venue', { method: 'POST', json: editForm.value })
  ElMessage.success('已保存')
  editDlg.value = false
  await load()
}

async function toggle(row: any) {
  await api(`/api/admin/venue/${row.id}/status`, { method: 'PUT' })
  await load()
}

onMounted(load)
</script>

<style scoped>
.bar { display: flex; align-items: center; gap: 10px; margin: 10px 0; }
</style>
