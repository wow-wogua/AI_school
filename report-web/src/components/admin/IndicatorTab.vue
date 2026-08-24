<template>
  <div>
    <h4>九维（系统契约基线，只读）</h4>
    <el-table :data="grids" size="small">
      <el-table-column prop="code" label="编码" width="100" />
      <el-table-column prop="name" label="名称" width="130" />
      <el-table-column prop="icon" label="图标" width="80" />
      <el-table-column prop="indicatorCount" label="指标数" width="90" />
      <el-table-column prop="sort" label="排序" width="80" />
    </el-table>

    <h4>二级指标（被评价记录引用的指标改名/删除会被拒绝）</h4>
    <div class="toolbar">
      <el-select v-model="gridId" placeholder="按九维筛选" clearable style="width: 180px" @change="loadIndicators">
        <el-option v-for="g in grids" :key="g.id" :label="g.name" :value="g.id" />
      </el-select>
      <el-button type="primary" @click="openCreate">新建指标</el-button>
    </div>
    <el-table :data="indicators" size="small">
      <el-table-column label="九维" width="110">
        <template #default="{ row }">{{ gridName(row.gridId) }}</template>
      </el-table-column>
      <el-table-column label="名称" width="170">
        <template #default="{ row }">{{ row.name === '' ? '（空名占位）' : row.name }}</template>
      </el-table-column>
      <el-table-column prop="direction" label="方向" width="80" />
      <el-table-column prop="defaultScore" label="默认分" width="90" />
      <el-table-column prop="subjectScope" label="学科范围" width="110" />
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑指标' : '新建指标'" width="440px">
      <el-form label-width="90px">
        <el-form-item label="所属九维">
          <el-select v-model="form.gridId" style="width: 100%">
            <el-option v-for="g in grids" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="方向">
          <el-select v-model="form.direction" style="width: 140px">
            <el-option label="加分" value="+" /><el-option label="减分" value="-" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认分">
          <el-input-number v-model="form.defaultScore" :step="1" controls-position="right" />
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

const grids = ref<any[]>([])
const indicators = ref<any[]>([])
const gridId = ref<number>()
const dialog = ref(false)
const form = ref<any>({})

function gridName(id: number) {
  return grids.value.find((g: any) => g.id === id)?.name ?? id
}

async function loadIndicators() {
  const qs = gridId.value ? `?gridId=${gridId.value}` : ''
  indicators.value = await api<any[]>(`/api/admin/indicator/list${qs}`)
}

function openCreate() {
  form.value = { gridId: gridId.value ?? grids.value[0]?.id, direction: '+', defaultScore: 1 }
  dialog.value = true
}

function openEdit(row: any) {
  form.value = { ...row }
  dialog.value = true
}

async function save() {
  if (form.value.id) {
    await api(`/api/admin/indicator/${form.value.id}`, { method: 'PUT', json: form.value })
  } else {
    await api('/api/admin/indicator', { method: 'POST', json: form.value })
  }
  ElMessage.success('已保存')
  dialog.value = false
  await loadIndicators()
}

async function remove(row: any) {
  await ElMessageBox.confirm(`删除指标 ${row.name || '(空名)'}？被引用时会被拒绝`, '确认')
  await api(`/api/admin/indicator/${row.id}`, { method: 'DELETE' })
  ElMessage.success('已删除')
  await loadIndicators()
}

onMounted(async () => {
  grids.value = await api<any[]>('/api/admin/grid/list')
  await loadIndicators()
})
</script>
