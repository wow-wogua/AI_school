<template>
  <div>
    <el-alert type="info" :closable="false" show-icon class="tip"
      title="面向毕业年级收档：按班打包导出学生照片/荣誉证书/微光照片/请假凭证/成长报告 PDF；导出过后才允许清理学生照片释放存储（档案数据保留）。" />
    <div class="pick-row">
      <el-select v-model="classId" filterable placeholder="选择班级" class="pick" @change="load">
        <el-option v-for="c in classes" :key="c.id" :value="c.id"
          :label="`${c.gradeName ? c.gradeName + ' · ' : ''}${c.name}${c.headTeacherName ? '（' + c.headTeacherName + '）' : ''}`" />
      </el-select>
    </div>

    <template v-if="info">
      <el-descriptions :column="3" border size="small" class="desc">
        <el-descriptions-item label="在读">{{ info.reading }} 人</el-descriptions-item>
        <el-descriptions-item label="学生总数">{{ info.students }} 人</el-descriptions-item>
        <el-descriptions-item label="文件总数">{{ info.totalFiles }} 个</el-descriptions-item>
        <el-descriptions-item label="学生照片">{{ info.photos }}</el-descriptions-item>
        <el-descriptions-item label="荣誉证书">{{ info.honors }}</el-descriptions-item>
        <el-descriptions-item label="微光照片">{{ info.moments }}</el-descriptions-item>
        <el-descriptions-item label="请假凭证">{{ info.leaves }}</el-descriptions-item>
        <el-descriptions-item label="成长报告">{{ info.reports }}</el-descriptions-item>
        <el-descriptions-item label="已导出">
          <el-tag v-if="info.archivedTime" type="success" size="small">{{ info.archivedTime.replace('T', ' ') }}</el-tag>
          <el-tag v-else type="info" size="small">未导出</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <div class="ops">
        <el-button type="primary" :loading="exporting" @click="doExport">导出归档包（zip）</el-button>
        <el-button type="danger" :disabled="!info.archivedTime" @click="doCleanup">清理学生照片</el-button>
        <span v-if="!info.archivedTime" class="hint">导出归档包后才可清理</span>
      </div>
    </template>
    <el-empty v-else description="选择班级查看归档内容" :image-size="80" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, apiBase } from '../../api/http'
import { useAuthStore } from '../../stores/auth'

const classes = ref<any[]>([])
const classId = ref<number>()
const info = ref<any>(null)
const exporting = ref(false)

async function load() {
  info.value = null
  if (!classId.value) return
  info.value = await api<any>(`/api/admin/archive/preview?classId=${classId.value}`)
}

/* 导出走原生 <a> 下载：zip 流式体积大，不走 fetch 拼 Blob（内存峰值高且无进度） */
function doExport() {
  exporting.value = true
  const a = document.createElement('a')
  a.href = apiBase() + `/api/admin/archive/export?classId=${classId.value}`
  // 鉴权走 header 的接口无法用 <a> 直达——归档导出允许 query token 兜底
  a.href += `&token=${encodeURIComponent(useAuthStore().token)}`
  a.download = ''
  a.click()
  exporting.value = false
  // 流结束后归档标记才落库，稍候刷新状态
  setTimeout(load, 4000)
}

async function doCleanup() {
  await ElMessageBox.confirm(
    `删除「${info.value.className}」全部学生照片（${info.value.photos} 张）？仅删照片释放存储，档案/报告等数据不动。`,
    '确认清理',
    { type: 'warning', confirmButtonText: '删除照片', cancelButtonText: '取消' },
  )
  const r = await api<{ deleted: number }>(`/api/admin/archive/cleanup?classId=${classId.value}`, { method: 'POST' })
  ElMessage.success(`已清理 ${r.deleted} 张学生照片`)
  await load()
}

onMounted(async () => {
  classes.value = await api<any[]>('/api/admin/class/list')
})
</script>

<style scoped>
.tip { margin-bottom: 12px; }
.pick-row { margin-bottom: 12px; }
.pick { max-width: 340px; width: 100%; }
.desc { margin-bottom: 14px; }
.ops { display: flex; align-items: center; gap: 10px; }
.hint { font-size: 12px; color: #909399; }
</style>
