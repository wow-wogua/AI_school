<template>
  <div>
    <div class="tip">
      发布安卓 App 新版本：上传后，老师们打开 App 会自动弹出更新提示，点击即可自助升级，无需重新分发安装包。
      版本号与 versionCode 在打包脚本输出里查看（新 versionCode 必须大于当前最新）。
    </div>

    <el-form class="publish" label-width="92px" @submit.prevent>
      <el-form-item label="版本号">
        <el-input v-model="form.versionName" placeholder="如 1.0.15" style="width: 180px" />
        <el-input v-model="form.versionCode" placeholder="versionCode（正整数）" style="width: 180px; margin-left: 8px" />
      </el-form-item>
      <el-form-item label="更新说明">
        <el-input v-model="form.notes" type="textarea" :rows="3" placeholder="一行一条，将逐条展示在老师的更新弹窗里" />
      </el-form-item>
      <el-form-item label="强制更新">
        <el-switch v-model="form.force" />
        <span class="hint">开启后老师必须更新才能继续使用（一般不用开）</span>
      </el-form-item>
      <el-form-item label="安装包">
        <input type="file" accept=".apk" @change="e => (file = (e.target as HTMLInputElement).files?.[0] || null)" />
        <span v-if="file" class="hint">{{ file.name }}（{{ mb(file.size) }}MB）</span>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="uploading" @click="publish">发布新版本</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" size="small">
      <el-table-column label="版本" width="110">
        <template #default="{ row }">v{{ row.versionName }}<span class="hint">（{{ row.versionCode }}）</span></template>
      </el-table-column>
      <el-table-column label="大小" width="90">
        <template #default="{ row }">{{ mb(row.size) }}MB</template>
      </el-table-column>
      <el-table-column prop="notes" label="更新说明" min-width="200" show-overflow-tooltip />
      <el-table-column label="强制" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.force" type="danger" size="small">强制</el-tag>
          <span v-else class="hint">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="uploader" label="发布人" width="90" />
      <el-table-column prop="createTime" label="发布时间" width="170" />
      <el-table-column label="操作" width="70">
        <template #default="{ row }">
          <el-button link type="danger" @click="revoke(row)">撤回</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, apiForm } from '../../api/http'

interface Release {
  id: number
  versionCode: number
  versionName: string
  notes: string
  size: number
  force: boolean
  uploader: string
  createTime: string
}

const form = ref({ versionName: '', versionCode: '', notes: '', force: false })
const file = ref<File | null>(null)
const uploading = ref(false)
const rows = ref<Release[]>([])

function mb(n: number): string {
  return (n / 1024 / 1024).toFixed(1)
}

async function load() {
  rows.value = await api<Release[]>('/api/admin/app/release/list')
}

async function publish() {
  if (!form.value.versionName.trim() || !form.value.versionCode.trim()) {
    ElMessage.warning('请填写版本号与 versionCode')
    return
  }
  if (!/^\d+$/.test(form.value.versionCode.trim())) {
    ElMessage.warning('versionCode 须为正整数')
    return
  }
  if (!file.value) {
    ElMessage.warning('请选择 .apk 安装包')
    return
  }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file.value)
    fd.append('versionName', form.value.versionName.trim())
    fd.append('versionCode', form.value.versionCode.trim())
    fd.append('notes', form.value.notes.trim())
    fd.append('force', String(form.value.force))
    await apiForm('/api/admin/app/release', fd)
    ElMessage.success('已发布，老师们打开 App 即会收到更新提示')
    form.value = { versionName: '', versionCode: '', notes: '', force: false }
    file.value = null
    await load()
  } finally {
    uploading.value = false
  }
}

async function revoke(row: Release) {
  const ok = await ElMessageBox.confirm(
    `撤回 v${row.versionName} 后，老师端将不再提示该版本${rows.value[0]?.id === row.id ? '（回退到前一版本或不再提示）' : ''}，确认撤回？`,
    '撤回版本', { type: 'warning' },
  ).then(() => true).catch(() => false)
  if (!ok) return
  await api(`/api/admin/app/release/${row.id}`, { method: 'DELETE' })
  ElMessage.success('已撤回')
  await load()
}

onMounted(load)
</script>

<style scoped>
.tip { margin-bottom: 14px; }
.publish { margin-bottom: 18px; }
.hint { font-size: 12px; color: var(--el-text-color-secondary); margin-left: 6px; }
</style>
