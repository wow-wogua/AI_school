<template>
  <div class="app-page classview">
    <!-- 头区（图4）：标题 + 校园全景照片带 + 班级切换 -->
    <div class="app-hero hero">
      <div class="hero-row">
        <div>
          <h1>成长档案</h1>
          <p>记录学生成长的每一时刻</p>
        </div>
        <button class="class-pick" type="button" @click="pickOpen = true">
          {{ curClassName }} <van-icon name="arrow-down" />
        </button>
      </div>
      <!-- 校园全景照片带（学校元素） -->
      <img class="hero-photo" src="/campus-pano.jpg" alt="石实实验学校">
      <!-- 搜索（图4：搜索学生姓名） -->
      <div class="search">
        <van-icon name="search" />
        <input v-model="keyword" placeholder="搜索学生姓名..." @input="debouncedLoad" />
      </div>
    </div>

    <!-- 本周微光：班级最近随手拍（横滑照片带；空态给拍照引导） -->
    <div class="app-card overlap moments">
      <div class="mo-head">
        <div class="app-sec mo-title" style="margin: 0"
          @click="$router.push({ path: '/moment', query: { classId: String(classId ?? '') } })">
          本周微光<span class="mo-cnt">{{ moments.length }}</span><van-icon class="mo-more" name="arrow" />
        </div>
        <button class="mo-cam" type="button"
          @click="$router.push({ path: '/moment/new', query: { classId: String(classId ?? '') } })">
          <van-icon name="photograph" /> 拍照
        </button>
      </div>
      <div v-if="moments.length" class="mo-row">
        <div v-for="m in moments" :key="m.id" class="mo-card">
          <MomentPhoto :url="m.photoUrl" @tap="(src) => showImagePreview({ images: [src] })" />
          <span class="mo-tag">{{ m.sceneTag }}</span>
        </div>
      </div>
      <button v-else class="mo-empty" type="button"
        @click="$router.push({ path: '/moment/new', query: { classId: String(classId ?? '') } })">
        <van-icon name="photograph" /> 拍下第一束微光，记录闪光时刻
      </button>
    </div>

    <!-- 学生卡片列表 -->
    <div v-if="students.length" class="app-card list" :class="{ overlap: !moments.length }">
      <div v-for="s in students" :key="s.id" class="stu" @click="$router.push(`/student/${s.id}`)">
        <span class="ava" :style="{ background: avaColor(s.name) }">{{ s.name.charAt(0) }}</span>
        <div class="stu-info">
          <span class="stu-name">{{ s.name }}</span>
          <span class="stu-no">学号 {{ s.studentNo || '—' }}</span>
        </div>
        <div class="stu-tags">
          <span class="app-chip">{{ s.gender || '未填' }}</span>
        </div>
        <van-icon class="stu-arrow" name="arrow" />
      </div>
    </div>
    <div v-else-if="!loading" class="app-card overlap list">
      <van-empty image-size="88" :description="keyword ? '没有找到该学生' : '本班暂无在读学生'" />
    </div>

    <p class="count">共 {{ total }} 名学生</p>

    <!-- 班级选择（底部弹层） -->
    <van-popup v-model:show="pickOpen" position="bottom" round>
      <van-picker title="选择班级" :columns="classColumns" @confirm="onPick" @cancel="pickOpen = false" />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { showImagePreview } from 'vant'
import { api } from '../api/http'
import MomentPhoto from '../components/MomentPhoto.vue'

interface Cls { id: number; name: string }
interface Stu { id: number; name: string; studentNo?: string; gender?: string }
interface MomentItem { id: number; photoUrl: string; sceneTag: string; note: string }

const classes = ref<Cls[]>([])
const classId = ref<number>()
const students = ref<Stu[]>([])
const moments = ref<MomentItem[]>([])
const total = ref(0)
const keyword = ref('')
const loading = ref(true)
const pickOpen = ref(false)

const curClassName = computed(() => classes.value.find((c) => c.id === classId.value)?.name ?? '选择班级')
const classColumns = computed(() => classes.value.map((c) => ({ text: c.name, value: c.id })))

function onPick({ value }: { value: number }) {
  classId.value = value
  pickOpen.value = false
  load()
  loadMoments()
}

async function loadMoments() {
  if (!classId.value) return
  api<MomentItem[]>(`/api/moment/class?classId=${classId.value}&limit=12`)
    .then((d) => (moments.value = d)).catch(() => (moments.value = []))
}

/** 头像底色：按姓名散列到一组柔和深浅蓝/暖色 */
const palette = ['#2F5FC0', '#7C4DD8', '#0D9467', '#B07A1C', '#D6567A', '#3A7CA5']
function avaColor(name: string) {
  let h = 0
  for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) % 997
  return palette[h % palette.length]
}

let debounce = 0
function debouncedLoad() {
  clearTimeout(debounce)
  debounce = window.setTimeout(load, 250)
}

async function load() {
  loading.value = true
  try {
    const d = await api<{ total: number; records: Stu[] }>(
      `/api/student/list?classId=${classId.value ?? ''}&keyword=${encodeURIComponent(keyword.value)}&page=1&size=100`,
    )
    students.value = d.records
    total.value = d.total
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  classes.value = await api<Cls[]>('/api/meta/my-classes')
  classId.value = classes.value[0]?.id
  await load()
  loadMoments()
})
</script>

<style scoped>
.hero-row { display: flex; align-items: flex-start; justify-content: space-between; }
.hero .hero-photo { height: 72px; }   /* 列表页用矮照片带，给学生列表让空间 */
.hero h1 { margin: 4px 0 2px; font-size: 21px; font-weight: 800; }
.hero p { margin: 0; font-size: 12px; color: rgba(255,255,255,.65); }
.class-pick { display: flex; align-items: center; gap: 4px; padding: 8px 14px;
  border: none; border-radius: 999px; background: rgba(255,255,255,.18); color: #fff;
  font-size: 13px; cursor: pointer; }

.search { display: flex; align-items: center; gap: 8px; margin-top: 14px; padding: 10px 14px;
  border-radius: 12px; background: rgba(255,255,255,.95); color: var(--app-text-3); }
.search .van-icon { font-size: 16px; }
.search input { flex: 1; border: none; outline: none; background: none; font-size: 14px; color: var(--app-text-1); }
.search input::placeholder { color: var(--app-text-3); }

/* 本周微光照片带 */
.moments { padding: 14px 14px 12px; }
.mo-head { display: flex; align-items: center; justify-content: space-between; }
.mo-title { display: flex; align-items: center; cursor: pointer; }
.mo-more { margin-left: 2px; font-size: 13px; color: #C6CDD9; }
.mo-cnt { margin-left: 6px; padding: 0 8px; border-radius: 999px; background: #FDEEE2;
  color: #EA580C; font-size: 11px; font-weight: 600; }
.mo-cam { display: flex; align-items: center; gap: 4px; padding: 6px 14px; border: none;
  border-radius: 999px; background: linear-gradient(150deg, #F97316, #EA580C);
  color: #fff; font-size: 12px; font-weight: 600; cursor: pointer; }
.mo-cam .van-icon { font-size: 14px; }
.mo-row { display: flex; gap: 10px; margin-top: 12px; overflow-x: auto;
  scrollbar-width: none; }
.mo-row::-webkit-scrollbar { display: none; }
.mo-card { position: relative; flex: none; width: 112px; height: 148px;
  border-radius: 12px; overflow: hidden; }
.mo-tag { position: absolute; left: 6px; bottom: 6px; padding: 2px 8px;
  border-radius: 999px; background: rgba(13,22,50,.55); color: #fff;
  font-size: 10px; backdrop-filter: blur(4px); }
.mo-empty { display: flex; align-items: center; justify-content: center; gap: 6px;
  width: 100%; margin-top: 12px; padding: 20px 0; border: 1.5px dashed #F3C9A8;
  border-radius: 12px; background: #FFF9F3; color: #EA580C; font-size: 13px; cursor: pointer; }
.mo-empty .van-icon { font-size: 16px; }

.list { padding: 4px 14px; }
.stu { display: flex; align-items: center; gap: 12px; padding: 11px 0; cursor: pointer; }
.stu + .stu { border-top: 1px solid var(--app-card-border); }
.stu:active { opacity: .75; }
.ava { display: flex; align-items: center; justify-content: center; width: 42px; height: 42px;
  border-radius: 50%; color: #fff; font-size: 16px; font-weight: 600; flex: none; }
.stu-info { flex: 1; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.stu-name { font-size: 15px; font-weight: 600; color: var(--app-text-1); }
.stu-no { font-size: 11px; color: var(--app-text-3); }
.stu-arrow { color: #C6CDD9; }
.count { margin: 14px 0 0; text-align: center; font-size: 12px; color: var(--app-text-3); }
</style>
