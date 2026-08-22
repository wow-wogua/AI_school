<template>
  <div class="page">
    <motion.h2 class="page-title" :initial="{ opacity: 0, x: -16 }" :animate="{ opacity: 1, x: 0 }"
      :transition="{ type: 'spring', stiffness: 400, damping: 32 }"><el-icon><View /></el-icon>报告预览</motion.h2>
    <div class="toolbar">
      <el-button @click="$router.back()">返回</el-button>
      <el-button @click="download">下载</el-button>
    </div>
    <div v-if="loading" style="padding: 60px; text-align: center; color: #909399">报告单加载中…</div>
    <iframe v-else-if="url" :src="url" class="frame" title="报告单预览" />
    <el-empty v-else description="加载失败" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { motion } from 'motion-v'
import { useRoute } from 'vue-router'
import { fetchBlob } from '../api/http'

const route = useRoute()
const url = ref('')
const loading = ref(true)

async function load() {
  const reportId = route.params.id as string
  try {
    const blob = await fetchBlob(`/api/report/file/${reportId}`)
    if (url.value) URL.revokeObjectURL(url.value)
    url.value = URL.createObjectURL(blob)
  } finally {
    loading.value = false
  }
}

function download() {
  const a = document.createElement('a')
  a.href = url.value
  a.download = '素质报告单.pdf'
  a.click()
}

onMounted(load)
onUnmounted(() => url.value && URL.revokeObjectURL(url.value))
</script>

<style scoped>
.frame { width: 100%; height: calc(100vh - 140px); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-md); box-shadow: var(--shadow-card); }
</style>
