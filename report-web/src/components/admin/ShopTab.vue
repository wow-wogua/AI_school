<template>
  <div>
    <h4>操行分规则（全校一套：余额=基础分+Σ事件增减；改基础分只影响此后初始化的新账户）</h4>
    <el-form :inline="true" label-width="90px">
      <el-form-item label="基础分"><el-input-number v-model="rule.baseScore" :min="0" :step="5" controls-position="right" /></el-form-item>
      <el-form-item label="A 级下限"><el-input-number v-model="rule.gradeAMin" :step="5" controls-position="right" /></el-form-item>
      <el-form-item label="B 级下限"><el-input-number v-model="rule.gradeBMin" :step="5" controls-position="right" /></el-form-item>
      <el-form-item label="C 级下限"><el-input-number v-model="rule.gradeCMin" :step="5" controls-position="right" /></el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="ruleSaving" @click="saveRule">保存规则</el-button>
        <span class="hint">须满足 A线 &gt; B线 &gt; C线，低于 C 线为 D</span>
      </el-form-item>
    </el-form>

    <h4>商品货架（教师 App 兑换录入；下架不出货架，历史流水不受影响）</h4>
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建商品</el-button>
    </div>
    <el-table :data="items" size="small">
      <el-table-column prop="name" label="商品" width="180" />
      <el-table-column prop="priceCoin" label="兑换价(能量币)" width="120" />
      <el-table-column label="库存" width="90">
        <template #default="{ row }">{{ row.stock < 0 ? '不限' : row.stock }}</template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="toggle(row)">
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <h4>兑换记录</h4>
    <el-table :data="expenses" size="small">
      <el-table-column prop="createTime" label="时间" width="160">
        <template #default="{ row }">{{ (row.createTime || '').slice(0, 16).replace('T', ' ') }}</template>
      </el-table-column>
      <el-table-column prop="studentName" label="学生" width="120" />
      <el-table-column prop="item" label="商品" min-width="140" />
      <el-table-column prop="coin" label="能量币" width="90" />
      <el-table-column prop="operatorName" label="录入教师" width="120" />
    </el-table>
    <el-pagination v-if="expenseTotal > 10" layout="total, prev, pager, next" :total="expenseTotal"
      :page-size="10" :current-page="expensePage" style="margin-top: 10px" @current-change="loadExpenses" />

    <el-dialog v-model="dialog" :title="form.id ? '编辑商品' : '新建商品'" width="440px">
      <el-form label-width="100px">
        <el-form-item label="商品名"><el-input v-model="form.name" placeholder="如：免作业券 / 文具盲盒" /></el-form-item>
        <el-form-item label="兑换价(币)">
          <el-input-number v-model="form.priceCoin" :min="0.01" :step="1" :precision="2" controls-position="right" />
        </el-form-item>
        <el-form-item label="库存">
          <el-input-number v-model="formStock" :min="-1" :step="1" controls-position="right" />
          <span class="hint">-1=不限量</span>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :step="1" controls-position="right" /></el-form-item>
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

const rule = ref<any>({ baseScore: 100, gradeAMin: 90, gradeBMin: 75, gradeCMin: 60 })
const ruleSaving = ref(false)
const items = ref<any[]>([])
const expenses = ref<any[]>([])
const expenseTotal = ref(0)
const expensePage = ref(1)
const dialog = ref(false)
const form = ref<any>({})
const formStock = ref(-1)

async function loadRule() {
  rule.value = await api<any>('/api/admin/conduct/rule') ?? rule.value
}

async function saveRule() {
  const { baseScore, gradeAMin, gradeBMin, gradeCMin } = rule.value
  if (!(gradeAMin > gradeBMin && gradeBMin > gradeCMin)) {
    ElMessage.warning('阈值必须满足 A线 > B线 > C线')
    return
  }
  ruleSaving.value = true
  try {
    await api('/api/admin/conduct/rule', { method: 'PUT', json: { baseScore, gradeAMin, gradeBMin, gradeCMin } })
    ElMessage.success('规则已保存')
  } finally { ruleSaving.value = false }
}

async function loadItems() {
  const d = await api<{ records: any[]; total: number }>('/api/admin/shop/item/list?page=1&size=100')
  items.value = d.records ?? []
}

function openCreate() {
  form.value = { name: '', priceCoin: 1 }
  formStock.value = -1
  dialog.value = true
}

function openEdit(row: any) {
  form.value = { ...row }
  formStock.value = row.stock ?? -1
  dialog.value = true
}

async function save() {
  const body = { name: form.value.name, priceCoin: form.value.priceCoin, stock: formStock.value, sort: form.value.sort ?? 0 }
  if (form.value.id) {
    await api(`/api/admin/shop/item/${form.value.id}`, { method: 'PUT', json: body })
  } else {
    await api('/api/admin/shop/item', { method: 'POST', json: body })
  }
  ElMessage.success('已保存')
  dialog.value = false
  await loadItems()
}

async function toggle(row: any) {
  await api(`/api/admin/shop/item/${row.id}/status`, { method: 'PUT', json: { status: row.status === 1 ? 0 : 1 } })
  ElMessage.success(row.status === 1 ? '已下架' : '已上架')
  await loadItems()
}

async function remove(row: any) {
  await ElMessageBox.confirm(`删除商品「${row.name}」？`, '确认')
  await api(`/api/admin/shop/item/${row.id}`, { method: 'DELETE' })
  ElMessage.success('已删除')
  await loadItems()
}

async function loadExpenses(page = 1) {
  expensePage.value = page
  const d = await api<{ records: any[]; total: number }>(`/api/admin/shop/expense/list?page=${page}&size=10`)
  expenses.value = d.records ?? []
  expenseTotal.value = d.total ?? 0
}

onMounted(async () => {
  await Promise.all([loadRule(), loadItems(), loadExpenses()])
})
</script>
