<template>
  <div class="asset-tab">
    <el-alert type="info" :closable="false" show-icon
      title="素材库：上传即生效，无需更新 App"
      description="App 内素材（主图/纹理）保存后全端立即可见；成长报告图库下一次生成报告即用新图。恢复内置即回到当前默认。建议照片 640 宽以上、横构图，jpg/png ≤10MB。" />

    <template v-for="g in groups" :key="g.name">
      <div class="sec">{{ g.name }}</div>
      <el-table :data="g.rows" size="small" v-loading="loading">
        <el-table-column prop="label" label="素材位" width="130" />
        <el-table-column label="用于" min-width="200">
          <template #default="{ row }">{{ row.scene }}</template>
        </el-table-column>
        <el-table-column label="当前状态" width="150">
          <template #default="{ row }">
            <el-tag v-if="!row.customized" size="small" type="info">内置默认</el-tag>
            <template v-else>
              <el-tag size="small" type="success">已自定义</el-tag>
              <div class="time">{{ row.updateTime }}</div>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="预览" width="90">
          <template #default="{ row }">
            <img v-if="row.customized" class="thumb" :src="`/api/asset/${row.key}?v=${row.version}`" alt="" />
            <span v-else class="hint">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <el-button link type="primary" :loading="busy === row.key" @click="pick(row)">更换图片</el-button>
            <el-button v-if="row.customized" link type="danger" @click="reset(row)">恢复内置</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <input ref="fileEl" type="file" accept="image/jpeg,image/png" class="hidden-input" @change="onFile" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, apiForm } from '../../api/http'

const loading = ref(false)
const rows = ref<any[]>([])
const busy = ref('')
const fileEl = ref<HTMLInputElement>()
let pickedKey = ''

const groups = computed(() => [
  { name: 'App 内素材', rows: rows.value.filter((r) => r.group === 'web') },
  { name: '成长报告图库（PDF）', rows: rows.value.filter((r) => r.group === 'pdf') },
  { name: 'IP 成长画像底图', rows: rows.value.filter((r) => r.group === 'ip') },
])

async function load() {
  loading.value = true
  try {
    rows.value = await api<any[]>('/api/admin/asset/list')
  } finally {
    loading.value = false
  }
}

function pick(row: any) {
  pickedKey = row.key
  fileEl.value!.value = ''
  fileEl.value!.click()
}

async function onFile(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (!f) return
  busy.value = pickedKey
  try {
    const fd = new FormData()
    fd.append('key', pickedKey)
    fd.append('file', f)
    rows.value = await apiForm<any[]>('/api/admin/asset/upload', fd)
    ElMessage.success('已生效')
  } finally {
    busy.value = ''
  }
}

async function reset(row: any) {
  await ElMessageBox.confirm(`恢复「${row.label}」为内置默认图？`, '确认')
  busy.value = row.key
  try {
    rows.value = await api<any[]>(`/api/admin/asset/${row.key}`, { method: 'DELETE' })
    ElMessage.success('已恢复内置')
  } finally {
    busy.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.asset-tab { max-width: 860px; }
.sec { margin: 14px 0 10px; padding-left: 8px; border-left: 3px solid var(--el-color-primary); font-weight: 600; }
.hint { font-size: 12px; color: var(--el-text-color-secondary); }
.time { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 2px; }
.thumb { width: 64px; height: 40px; object-fit: cover; border-radius: 4px; display: block; }
.hidden-input { display: none; }
</style>
