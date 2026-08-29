<template>
  <!-- 微光照片：JWT 拉流 → objectURL（父层经 @tap 拿 src 做全屏预览） -->
  <img v-if="src" :src="src" class="moment-photo" alt="微光照片" @click="$emit('tap', src)">
  <div v-else class="moment-photo loading"><van-icon name="photo-o" /></div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { fetchBlob } from '../api/http'

const props = defineProps<{ url: string }>()
defineEmits<{ (e: 'tap', src: string): void }>()

const src = ref('')
let objectUrl = ''

onMounted(async () => {
  try {
    const blob = await fetchBlob(props.url)
    objectUrl = URL.createObjectURL(blob)
    src.value = objectUrl
  } catch { /* 照片加载失败保持占位 */ }
})

onUnmounted(() => {
  if (objectUrl) URL.revokeObjectURL(objectUrl)
})
</script>

<style scoped>
.moment-photo { display: block; width: 100%; height: 100%; object-fit: cover; }
.loading { display: flex; align-items: center; justify-content: center;
  background: #EEF2F8; color: #B9C3D4; font-size: 20px; }
</style>
