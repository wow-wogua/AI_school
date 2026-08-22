<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建学期</el-button>
    </div>
    <el-table :data="terms" size="small">
      <el-table-column prop="name" label="名称" width="180" />
      <el-table-column prop="startDate" label="开始" width="130" />
      <el-table-column prop="endDate" label="结束" width="130" />
      <el-table-column label="当前学期" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.isCurrent === 1" type="success" size="small">当前</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.isCurrent !== 1" link type="success" @click="activate(row)">设为当前</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑学期' : '新建学期'" width="440px">
      <el-form label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="如 2026年秋季学期" /></el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="设为当前学期">
          <el-switch v-model="isCurrent" :active-value="1" :inactive-value="0"
            :disabled="form.id && currentId === form.id" />
          <span class="hint">设为当前后其余学期自动取消</span>
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
import { api } from '../../api/http'

const terms = ref<any[]>([])
const dialog = ref(false)
const form = ref<any>({})
const isCurrent = ref(0)
const currentId = ref<number>()

async function load() {
  terms.value = await api<any[]>('/api/admin/term/list')
  currentId.value = terms.value.find((t: any) => t.isCurrent === 1)?.id
}

function openCreate() {
  form.value = {}
  isCurrent.value = 0
  dialog.value = true
}

function openEdit(row: any) {
  form.value = { ...row }
  isCurrent.value = row.isCurrent
  dialog.value = true
}

async function save() {
  const body = { ...form.value, isCurrent: isCurrent.value }
  if (form.value.id) {
    await api(`/api/admin/term/${form.value.id}`, { method: 'PUT', json: body })
  } else {
    await api('/api/admin/term', { method: 'POST', json: body })
  }
  ElMessage.success('已保存')
  dialog.value = false
  await load()
}

async function activate(row: any) {
  await ElMessageBox.confirm(`将 ${row.name} 设为当前学期？`, '确认')
  await api(`/api/admin/term/${row.id}`, { method: 'PUT', json: { ...row, isCurrent: 1 } })
  ElMessage.success('已设为当前学期')
  await load()
}

async function remove(row: any) {
  await ElMessageBox.confirm(`删除学期 ${row.name}？有考试的学期会被拒绝`, '确认')
  await api(`/api/admin/term/${row.id}`, { method: 'DELETE' })
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.hint { color: #909399; font-size: 12px; margin-left: 8px; }
</style>
