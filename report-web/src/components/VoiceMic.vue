<!-- 批39⑧ 语音输入（原生插件 @capacitor-community/speech-recognition）：
     仅 App 内渲染（浏览器/网页形态不显示）；设备无语音识别服务（国内无 GMS 常见）自动隐藏。
     转写结果整段经 text 事件交父组件拼接，中间过程只做按钮态提示 -->
<template>
  <button v-if="supported" type="button" class="voice-mic" :class="{ on: listening }" @click="toggle">
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
      stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <rect x="9" y="3" width="6" height="11" rx="3" />
      <path d="M5 11a7 7 0 0 0 14 0" />
      <line x1="12" y1="18" x2="12" y2="21" />
    </svg>
    <span>{{ listening ? '正在听 · 点击结束' : '语音输入' }}</span>
  </button>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Capacitor, type PluginListenerHandle } from '@capacitor/core'
import { SpeechRecognition } from '@capacitor-community/speech-recognition'

const emit = defineEmits<{ text: [s: string] }>()

const supported = ref(false)
const listening = ref(false)
let partial = ''            // 本次语音段实时转写（partial 持续覆盖，结束时整段上屏）
let startedByMe = false     // 区分「我点停」与「引擎自己停了」——两者都要收尾上屏
let handles: PluginListenerHandle[] = []

onMounted(async () => {
  // 网页形态（含教师电脑浏览器）无原生语音能力，直接不渲染
  if (!Capacitor.isNativePlatform()) return
  try {
    const r = await SpeechRecognition.available()
    supported.value = !!r.available
    if (!supported.value) return
    handles = await Promise.all([
      SpeechRecognition.addListener('partialResults', (d) => {
        partial = (d.matches || [])[0] ?? ''
      }),
      SpeechRecognition.addListener('listeningState', (d) => {
        if (d.status === 'stopped' && startedByMe) finish()
      }),
    ])
  } catch { /* 个别 ROM 查询即抛异常：当作不支持 */ }
})

async function toggle() {
  if (listening.value) {
    await SpeechRecognition.stop().catch(() => {})
    finish()
    return
  }
  const p = await SpeechRecognition.checkPermissions()
  if (p.speechRecognition !== 'granted') {
    const r = await SpeechRecognition.requestPermissions()
    if (r.speechRecognition !== 'granted') {
      ElMessage.warning('未授予麦克风权限，无法语音输入')
      return
    }
  }
  partial = ''
  try {
    startedByMe = true
    // popup:false 才有 partialResults（Android 插件约束）；zh-CN 中文识别
    await SpeechRecognition.start({ language: 'zh-CN', partialResults: true, popup: false, maxResults: 1 })
    listening.value = true
  } catch (e) {
    startedByMe = false
    ElMessage.error('语音识别启动失败，请重试')
    throw e
  }
}

function finish() {
  startedByMe = false
  listening.value = false
  const t = partial.trim()
  partial = ''
  if (t) emit('text', t)
}

onBeforeUnmount(async () => {
  for (const h of handles) await h.remove().catch(() => {})
  if (startedByMe) await SpeechRecognition.stop().catch(() => {})
})
</script>

<style scoped>
.voice-mic { display: inline-flex; align-items: center; gap: 5px; padding: 5px 12px;
  border-radius: 14px; border: 1px solid var(--el-border-color); background: var(--el-bg-color);
  color: var(--el-text-color-regular); font-size: 12px; line-height: 1; cursor: pointer; }
.voice-mic:active { opacity: .75; }
.voice-mic.on { border-color: #A8232B; background: #A8232B; color: #fff; animation: vmic 1.2s ease-in-out infinite; }
@keyframes vmic { 50% { opacity: .65; } }
</style>
