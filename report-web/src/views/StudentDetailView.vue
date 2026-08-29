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

    <!-- 功能入口：跳到各功能页（寄语/成长总结支持带学生预选） -->
    <div class="app-card overlap grid">
      <button v-for="g in entries" :key="g.name" class="g-item" type="button" @click="go(g)">
        <span class="g-icon" :style="{ background: g.bg }"><van-icon :name="g.icon" /></span>
        <span>{{ g.name }}</span>
      </button>
    </div>

    <!-- 基本信息卡 -->
    <div class="app-card info">
      <div class="app-sec" style="margin: 0 0 6px">基本信息</div>
      <van-cell title="状态" :value="stu.status || '—'" />
      <van-cell title="家长" :value="stu.guardianName || '—'" />
      <van-cell title="联系电话" :value="stu.guardianPhone || '—'" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api/http'

const route = useRoute()
const router = useRouter()

interface Stu { name?: string; gender?: string; studentNo?: string; classId?: number; status?: string; guardianName?: string; guardianPhone?: string }
const stu = ref<Stu>({})
const className = ref('')
const termId = ref<number>()

/* 入口配色（图4）：每格一色的实心圆角方底 + 白图标 */
const entries = [
  { name: '成绩', icon: 'bar-chart-o', to: '/scores', bg: '#3E7BFA', preselect: false },
  { name: '日常评价', icon: 'edit', to: '/evaluate', bg: '#10B981', preselect: false },
  { name: '活动', icon: 'flag-o', to: '/activity', bg: '#F43F5E', preselect: false },
  { name: '荣誉', icon: 'medal-o', to: '/honor', bg: '#EAB308', preselect: false },
  { name: '寄语', icon: 'chat-o', to: '/comments', bg: '#F59E0B', preselect: true },
  { name: '成长总结', icon: 'notes-o', to: '/summary', bg: '#8B5CF6', preselect: true },
  { name: '综合素质', icon: 'gem-o', to: '/comprehensive', bg: '#0EA5E9', preselect: false },
  { name: '时间轴', icon: 'clock-o', to: '/timeline', bg: '#6366F1', preselect: false },
]

/** 寄语/成长总结页支持 query 预选学生（阶段3其余页面补齐预选） */
function go(g: (typeof entries)[number]) {
  const studentId = Number(route.params.id)
  if (g.preselect && studentId && termId.value) {
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
  const terms = await api<{ id: number; name: string }[]>('/api/meta/terms')
  termId.value = terms[0]?.id
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

.info { margin-top: 12px; padding: 14px 4px 4px; }
.info :deep(.van-cell) { font-size: 14px; }
</style>
