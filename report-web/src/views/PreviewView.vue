<template>
  <div class="page">
    <div class="toolbar">
      <el-button v-if="isNative" type="primary" :loading="shareBusy" @click="openShare">打开 / 保存 PDF</el-button>
      <el-button v-else type="primary" @click="download">下载 PDF</el-button>
    </div>
    <div v-if="loading" style="padding: 60px; text-align: center; color: #909399">报告单加载中…</div>
    <!-- 安卓 WebView 无 PDF 渲染器（iframe 白屏）：改为调系统分享面板，选查看器=预览、保存到文件=下载 -->
    <div v-else-if="isNative" class="native-card">
      <p class="nc-title">{{ fileName }}</p>
      <p class="nc-tip">报告已就绪。点击「打开 / 保存 PDF」：<br>选 WPS / 浏览器等查看器 = 预览；选「保存到文件」= 存到手机。</p>
    </div>
    <iframe v-else-if="url" :src="url" class="frame" title="报告单预览" />
    <el-empty v-else description="加载失败" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { fetchBlob } from '../api/http'
import { isNative, openFile } from '../api/nativeShare'

const route = useRoute()
const url = ref('')
const blob = ref<Blob>()
const loading = ref(true)
const shareBusy = ref(false)
const fileName = ref('素质报告单.pdf')

async function load() {
  const reportId = route.params.id as string
  const name = (route.query.name as string) || ''
  fileName.value = `${name ? name + '-' : ''}素质报告单.pdf`
  try {
    if (isNative) {
      blob.value = await fetchBlob(`/api/report/file/${reportId}`)
      openShare()   // 用户点的就是「预览」：就绪即调系统面板
    } else {
      const b = await fetchBlob(`/api/report/file/${reportId}`)
      if (url.value) URL.revokeObjectURL(url.value)
      url.value = URL.createObjectURL(b)
    }
  } finally {
    loading.value = false
  }
}

async function openShare() {
  if (!blob.value || shareBusy.value) return
  shareBusy.value = true
  try {
    await openFile(blob.value, fileName.value)
  } finally {
    shareBusy.value = false
  }
}

function download() {
  const a = document.createElement('a')
  a.href = url.value
  a.download = fileName.value
  a.click()
}

onMounted(load)
onUnmounted(() => url.value && URL.revokeObjectURL(url.value))
</script>

<style scoped>
.frame { width: 100%; height: calc(100dvh - 175px); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-md); box-shadow: var(--shadow-card); }
.native-card { padding: 48px 24px; text-align: center; }
.nc-title { margin: 0 0 10px; font-size: 16px; font-weight: 600; }
.nc-tip { margin: 0; font-size: 13px; line-height: 1.9; color: #909399; }
</style>
