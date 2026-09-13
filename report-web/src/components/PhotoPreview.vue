<template>
  <!-- 照片全屏预览：基于 van-image-preview（保留左右滑动/页码），按钮走 #cover slot
     ——不能用 #image slot 叠 fixed 按钮：swipe 滑动层的 transform 会废掉 fixed（按钮飘走，
     点击落在图片上=误触退出大图，真机踩过）。cover 在 swipe 外层的弹层容器上，fixed 可靠。
     点击=保存/分享面板（App=系统分享面板：微信/QQ/保存到文件自选；浏览器=直接下载） -->
  <van-image-preview v-model:show="visible" :images="images" :start="start" teleport="body" @change="(i: number) => (cur = i)">
    <template #image="{ src }">
      <img class="pv-img" :src="src" alt="微光照片">
    </template>
    <template #cover>
      <button class="pv-save" type="button" @click.stop="save(images[cur])">
        <van-icon name="down" /> 保存 / 分享
      </button>
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
const cur = ref(0)

/** 打开预览：单图 open([src])，多图 open(list, idx) */
function open(list: string[], idx = 0) {
  images.value = list
  start.value = idx
  cur.value = idx
  visible.value = true
}
defineExpose({ open })

async function save(src?: string) {
  if (!src) return
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
.pv-img { max-width: 100vw; max-height: 100%; object-fit: contain; }
/* #cover 容器是 top:0 的 absolute 且无宽，按钮自己 fixed 到视口底部居中 */
.pv-save { position: fixed; left: 50%; transform: translateX(-50%);
  bottom: calc(30px + var(--sab, 0px));
  display: flex; align-items: center; gap: 6px; padding: 10px 26px;
  border: none; border-radius: 999px; background: rgba(255,255,255,.92); color: var(--app-blue-deep);
  font-size: 14px; font-weight: 600; cursor: pointer;
  box-shadow: 0 4px 16px rgba(0,0,0,.35); -webkit-tap-highlight-color: transparent; }
.pv-save:active { opacity: .8; }
</style>
