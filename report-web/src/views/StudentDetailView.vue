<template>
  <div class="app-page detail">
    <!-- 顶栏：返回 + 渐变头区（学生信息） -->
    <div class="app-hero hero">
      <button class="back" type="button" aria-label="返回" @click="$router.back()">
        <van-icon name="arrow-left" />
      </button>
      <img class="hero-photo" src="/campus-bg.jpg" alt="石实实验学校">
      <div class="stu-head">
        <span class="ava" :style="{ background: avaColor(stu.name) }">{{ stu.name?.charAt(0) }}</span>
        <div class="stu-brief">
          <h1>{{ stu.name }}</h1>
          <p>{{ className }}<template v-if="stu.gender"> · {{ stu.gender }}</template><template v-if="stu.studentNo"> · 学号 {{ stu.studentNo }}</template></p>
        </div>
      </div>
    </div>

    <!-- 功能入口：跳到各功能页（除活动外全部带该生预选，打开即是 TA 的数据） -->
    <div class="app-card overlap tl tex-a grid">
      <button v-for="g in entries" :key="g.name" class="g-item" type="button" @click="go(g)">
        <span class="g-icon" :style="{ background: g.bg }"><van-icon :name="g.icon" /></span>
        <span>{{ g.name }}</span>
      </button>
    </div>

    <!-- TA的闪光时刻：微光照片墙（有微光才显示） -->
    <div v-if="moments.length" class="app-card tl gold tex-d moments">
      <div class="app-sec" style="margin: 0 0 10px">TA的闪光时刻<span class="mo-cnt">{{ moments.length }}</span></div>
      <div class="mo-grid">
        <div v-for="m in moments" :key="m.id" class="mo-item">
          <MomentPhoto :url="m.photoUrl" @tap="(src) => showImagePreview({ images: [src] })" />
          <span class="mo-tag">{{ m.sceneTag }}</span>
        </div>
      </div>
    </div>

    <!-- 基本信息卡（学籍卡风格） -->
    <div class="app-card tl tex-e info">
      <div class="app-sec" style="margin: 0 0 6px">基本信息<span class="card-tag">学籍卡</span></div>
      <van-cell title="状态" :value="stu.status || '—'" />
      <van-cell title="家长" :value="stu.guardianName || '—'" />
      <van-cell title="联系电话" :value="stu.guardianPhone || '—'" />
      <div class="barcode" aria-hidden="true"><i v-for="n in 24" :key="n" :style="{ opacity: n % 3 ? .8 : .35 }"></i></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showImagePreview } from 'vant'
import { api } from '../api/http'
import MomentPhoto from '../components/MomentPhoto.vue'

const route = useRoute()
const router = useRouter()

interface Stu { name?: string; gender?: string; studentNo?: string; classId?: number; status?: string; guardianName?: string; guardianPhone?: string }
const stu = ref<Stu>({})
const className = ref('')
const termId = ref<number>()
const moments = ref<{ id: number; photoUrl: string; sceneTag: string }[]>([])

/* 入口配色（图4）：每格一色的实心圆角方底 + 白图标；noPre=目标页无按学生看数据的形态 */
const entries = [
  { name: '成绩', icon: 'bar-chart-o', to: '/scores', bg: '#3E7BFA' },
  { name: '日常评价', icon: 'edit', to: '/evaluate', bg: '#10B981' },
  { name: '活动', icon: 'flag-o', to: '/activity', bg: '#F43F5E', noPre: true },
  { name: '荣誉', icon: 'medal-o', to: '/honor', bg: '#EAB308' },
  { name: '寄语', icon: 'chat-o', to: '/comments', bg: '#F59E0B' },
  { name: '成长总结', icon: 'notes-o', to: '/summary', bg: '#8B5CF6' },
  { name: '综合素质', icon: 'gem-o', to: '/comprehensive', bg: '#0EA5E9' },
  { name: '时间轴', icon: 'clock-o', to: '/timeline', bg: '#6366F1' },
]

/** 除「活动」外全部带该生预选（目标页 init 读 query 自动选中班级+学生） */
function go(g: (typeof entries)[number]) {
  const studentId = Number(route.params.id)
  if (!g.noPre && studentId && termId.value) {
    router.push({ path: g.to, query: { studentId: String(studentId), termId: String(termId.value) } })
  } else {
    router.push(g.to)
  }
}

const palette = ['#2F5FC0', '#7C4DD8', '#0D9467', '#B07A1C', '#D6567A', '#3A7CA5']
function avaColor(name?: string) {
  if (!name) return palette[0]
  let h = 0
  for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) % 997
  return palette[h % palette.length]
}

onMounted(async () => {
  const id = Number(route.params.id)
  stu.value = await api<Stu>(`/api/student/${id}`)
  const classes = await api<{ id: number; name: string }[]>('/api/meta/my-classes')
  className.value = classes.find((c) => c.id === stu.value.classId)?.name ?? ''
  const terms = await api<{ id: number; name: string; isCurrent?: number }[]>('/api/meta/terms')
  termId.value = terms.find((t) => t.isCurrent === 1)?.id ?? terms[0]?.id
  api<{ id: number; photoUrl: string; sceneTag: string }[]>(`/api/moment/student?studentId=${id}`)
    .then((d) => (moments.value = d)).catch(() => {})
})
</script>

<style scoped>
.hero { padding-bottom: 58px; }
.back { display: flex; align-items: center; justify-content: center; width: 34px; height: 34px;
  margin-bottom: 8px; border: none; border-radius: 50%; background: rgba(255,255,255,.16); color: #fff;
  cursor: pointer; }
.stu-head { display: flex; align-items: center; gap: 14px; }
.ava { display: flex; align-items: center; justify-content: center; width: 58px; height: 58px;
  border-radius: 50%; border: 2px solid rgba(255,255,255,.4); color: #fff; font-size: 22px; font-weight: 700; }
.stu-brief h1 { margin: 0; font-size: 21px; font-weight: 800; }
.stu-brief p { margin: 4px 0 0; font-size: 12px; color: rgba(255,255,255,.72); }

.grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px 0; padding: 12px 6px; }
@media (min-width: 600px) { .grid { grid-template-columns: repeat(4, 1fr); } }
.g-item { display: flex; flex-direction: column; align-items: center; gap: 7px;
  padding: 8px 2px; background: none; border: none; color: var(--app-text-1);
  font-size: 12px; cursor: pointer; -webkit-tap-highlight-color: transparent; }
.g-item:active { opacity: .7; }
.g-icon { display: flex; align-items: center; justify-content: center; width: 44px; height: 44px;
  border-radius: 14px; box-shadow: 0 3px 8px rgba(23,43,99,.14); }
.g-icon .van-icon { font-size: 22px; color: #fff; }

.moments { margin-top: 12px; padding: 14px 14px 12px; }
.mo-cnt { margin-left: 6px; padding: 0 8px; border-radius: 999px; background: #FDEEE2;
  color: #EA580C; font-size: 11px; font-weight: 600; }
.mo-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.mo-item { position: relative; aspect-ratio: 1/1; border-radius: 10px; overflow: hidden; }
.mo-tag { position: absolute; left: 4px; bottom: 4px; padding: 1px 7px;
  border-radius: 999px; background: rgba(13,22,50,.55); color: #fff;
  font-size: 10px; backdrop-filter: blur(4px); }

.info { margin-top: 12px; padding: 14px 4px 12px; }
.info :deep(.van-cell) { font-size: 14px; }
.card-tag { margin-left: 8px; padding: 1px 8px; border: 1px solid #D9E1F0; border-radius: 4px;
  font-size: 10px; font-weight: 500; color: var(--app-text-3); letter-spacing: 2px; }
.barcode { display: flex; align-items: center; justify-content: center; gap: 3px; height: 26px;
  margin-top: 8px; }
.barcode i { width: 2px; height: 100%; background: #2F5FC0; border-radius: 1px; }
.barcode i:nth-child(2n) { width: 1px; }
</style>
