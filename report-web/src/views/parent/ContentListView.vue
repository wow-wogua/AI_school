<template>
  <div class="app-page p-contents">
    <van-pull-refresh v-model="refreshing" @refresh="reload" success-text="已刷新">
    <div class="app-sec">{{ typeLabel }} · {{ rows.length }} 条</div>

    <van-skeleton v-if="loading" :row="6" style="padding: 14px" />
    <div v-else-if="!rows.length" class="app-card empty">
      {{ type === 'NOTICE' ? '暂无通知公告' : '暂无育儿课堂内容' }}
    </div>

    <!-- 内容卡（第一版卡片语言：封面通栏图 + 标题 + 摘要，tex 纹理轮换） -->
    <div v-for="(it, i) in rows" :key="it.id" class="app-card item" :class="tex(i)"
      @click="$router.push(`/p/content/${it.id}`)">
      <img v-if="coverCache[it.id]" class="cover" :src="coverCache[it.id]" alt="" />
      <div class="body">
        <p class="title">
          {{ it.title }}
          <span v-if="it.videoUrl" class="app-chip video">视频</span>
          <span v-if="it.scope === 'CLASS'" class="app-chip cls">{{ it.className ?? '本班' }}</span>
        </p>
        <p v-if="it.content" class="brief">{{ it.content }}</p>
        <p class="meta">{{ fmtDate(it.publishTime) }}</p>
      </div>
    </div>

    <p class="app-foot">石实实验学校 · 石实SHINE</p>
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { api, fetchBlob } from '../../api/http'

/* 通知公告 / 育儿课堂 共用列表页（路由 props 区分 type） */
const props = defineProps<{ type: 'NOTICE' | 'PARENTING' }>()
const typeLabel = props.type === 'NOTICE' ? '通知公告' : '育儿课堂'

interface Item {
  id: number; title: string; coverUrl: string | null; videoUrl: string | null
  content: string | null; scope: string; className: string | null; publishTime: string | null
}

const rows = ref<Item[]>([])
const loading = ref(true)
const refreshing = ref(false)
const coverCache = reactive<Record<number, string>>({})

function tex(i: number) {
  return ['tex-a', 'tex-b', 'tex-c', 'tex-e', 'tex-f', 'tex-g'][i % 6]
}
function fmtDate(t?: string | null) {
  return t ? t.slice(0, 10) : ''
}

async function load() {
  loading.value = true
  try {
    rows.value = await api<Item[]>(`/api/parent/contents?type=${props.type}`)
    for (const it of rows.value) {
      if (it.coverUrl && !coverCache[it.id]) {
        try {
          const blob = await fetchBlob(`/api/content/file/${it.id}`)
          coverCache[it.id] = URL.createObjectURL(blob)
        } catch { /* 封面缺失不阻塞列表 */ }
      }
    }
  } finally {
    loading.value = false
  }
}

async function reload() {
  try { await load() } finally { refreshing.value = false }
}

/* 两路由共用本组件，vue-router 切换时复用实例（onMounted 不重跑）——type 变化须重拉 */
watch(() => props.type, () => load())
onMounted(load)
</script>

<style scoped>
/* C 风格页面：结构复用第一版全局类，点缀色 C 令牌 */
.empty { text-align: center; color: var(--app-text-2); font-size: 13px; padding: 26px 0; }

.item { padding: 0; overflow: hidden; margin-bottom: 12px; cursor: pointer; }
.item:active { opacity: .85; }
.cover { display: block; width: 100%; aspect-ratio: 16 / 9; object-fit: cover; }
.body { padding: 12px 14px 10px; }
.title { margin: 0; font-size: 15px; font-weight: 600; color: var(--app-text-1);
  display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.title .app-chip { flex: none; }
.video { color: var(--shine-red); background: #fff; border: 1px solid var(--shine-red-soft); }
.cls { color: var(--shine-navy); background: #fff; border: 1px solid var(--shine-line); }
.brief { margin: 6px 0 0; font-size: 12px; color: var(--app-text-3);
  line-height: 1.6; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.meta { margin: 8px 0 0; font-size: 11px; color: var(--app-text-3); }
</style>
