<template>
  <!-- 照片全屏预览：基于 van-image-preview（保留左右滑动/页码），#image slot 叠加「保存/分享」
       ——老师把微光时刻存下来发家长群的核心出口（App=系统分享面板，浏览器=直接下载） -->
  <van-image-preview v-model:show="visible" :images="images" :start="start" teleport="body">
    <template #image="{ src }">
      <div class="pv-wrap">
        <img class="pv-img" :src="src" alt="微光照片" @click="visible = false">
        <button class="pv-save" type="button" @click.stop="save(src)">
          <van-icon name="down" /> 保存 / 分享
        </button>
      </div>
    </template>
  </van-image-preview>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { showSuccessToast, showToast } from 'vant'
import { isNative, saveFile } from '../api/nativeShare'

const visible = ref(false)
const images = ref<string[]>([])
const start = ref(0)

/** 打开预览：单图 open([src])，多图 open(list, idx) */
function open(list: string[], idx = 0) {
  images.value = list
  start.value = idx
  visible.value = true
}
defineExpose({ open })

async function save(src: string) {
  try {
    const blob = await (await fetch(src)).blob()
    const day = new Date().toISOString().slice(0, 10)
    await saveFile(blob, `微光时刻_${day}.jpg`)
    showSuccessToast(isNative ? '已打开分享面板' : '已保存到下载')
  } catch {
    showToast('保存失败，请重试')
  }
}
</script>

<style scoped>
.pv-wrap { position: relative; display: flex; align-items: center; justify-content: center;
  width: 100vw; height: 100%; }
.pv-img { max-width: 100vw; max-height: 80vh; object-fit: contain; }
.pv-save { position: fixed; left: 50%; transform: translateX(-50%);
  bottom: calc(30px + var(--sab, 0px));
  display: flex; align-items: center; gap: 6px; padding: 10px 26px;
  border: none; border-radius: 999px; background: rgba(255,255,255,.92); color: var(--app-blue-deep);
  font-size: 14px; font-weight: 600; cursor: pointer;
  box-shadow: 0 4px 16px rgba(0,0,0,.35); -webkit-tap-highlight-color: transparent; }
.pv-save:active { opacity: .8; }
</style>
