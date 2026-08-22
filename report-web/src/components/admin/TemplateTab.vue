<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建草稿模板</el-button>
    </div>
    <el-table :data="templates" size="small">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="schoolName" label="学校名" width="200" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <template v-if="row.status === '启用'">
            <el-tooltip content="启用模板为契约基线，锁定只读">
              <el-button link disabled>锁定</el-button>
            </el-tooltip>
          </template>
          <template v-else>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑草稿' : '新建草稿'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="学校名"><el-input v-model="form.schoolName" /></el-form-item>
        <el-form-item label="板块 JSON">
          <el-input v-model="form.sections" type="textarea" :rows="10" placeholder='{"sections": {...}}' />
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

const templates = ref<any[]>([])
const dialog = ref(false)
const form = ref<any>({})

async function load() {
  templates.value = await api<any[]>('/api/admin/template/list')
}

function openCreate() {
  form.value = { schoolName: '', sections: '{}' }
  dialog.value = true
}

function openEdit(row: any) {
  form.value = { ...row }
  dialog.value = true
}

async function save() {
  try {
    JSON.parse(form.value.sections)
  } catch {
    ElMessage.error('板块必须是合法 JSON')
    return
  }
  if (form.value.id) {
    await api(`/api/admin/template/${form.value.id}`, { method: 'PUT', json: form.value })
  } else {
    await api('/api/admin/template', { method: 'POST', json: form.value })
  }
  ElMessage.success('已保存（草稿）')
  dialog.value = false
  await load()
}

async function remove(row: any) {
  await ElMessageBox.confirm(`删除草稿模板 ${row.schoolName}？`, '确认')
  await api(`/api/admin/template/${row.id}`, { method: 'DELETE' })
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>
