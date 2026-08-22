<template>
  <div>
    <div class="toolbar">
      <el-input v-model="username" placeholder="操作人用户名" style="width: 160px" clearable @change="load" />
      <el-input v-model="keyword" placeholder="接口/参数关键字" style="width: 200px" clearable @change="load" />
      <span class="hint">记录所有写操作（密码类接口不记参数）</span>
    </div>

    <el-table :data="records" size="small">
      <el-table-column prop="createTime" label="时间" width="170">
        <template #default="{ row }">{{ fmt(row.createTime) }}</template>
      </el-table-column>
      <el-table-column prop="username" label="操作人" width="110" />
      <el-table-column label="方法" width="70">
        <template #default="{ row }">
          <el-tag size="small" :type="row.method === 'DELETE' ? 'danger' : row.method === 'PUT' ? 'warning' : 'primary'">
            {{ row.method }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="uri" label="接口" min-width="220" show-overflow-tooltip />
      <el-table-column prop="body" label="参数摘要" min-width="240" show-overflow-tooltip />
      <el-table-column label="结果" width="70">
        <template #default="{ row }">{{ row.status }}</template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="total > 20" layout="prev, pager, next" :total="total" :page-size="20"
      :current-page="page" @current-change="(p: number) => { page = p; load() }" style="margin-top: 10px" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api/http'

const records = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const username = ref('')
const keyword = ref('')

function fmt(v?: string) {
  return v ? String(v).replace('T', ' ').slice(0, 19) : ''
}

async function load() {
  const qs = new URLSearchParams({ page: String(page.value), size: '20' })
  if (username.value) qs.set('username', username.value)
  if (keyword.value) qs.set('keyword', keyword.value)
  const d = await api<{ total: number; records: any[] }>(`/api/admin/audit/list?${qs}`)
  total.value = d.total
  records.value = d.records
}

onMounted(load)
</script>

<style scoped>
.hint { font-size: 12px; color: var(--el-text-color-secondary); }
</style>
