<template>
  <div class="page" :class="{ 'admin-desktop': desktop }">
    <!-- 桌面 ≥1024：侧栏形态（c/admin-desktop.html 设计稿；App 壳此形态下不渲染返回导航条） -->
    <template v-if="desktop">
      <aside class="aside">
        <div class="logo">
          <img src="/badge.png" alt="校徽">
          <div class="lb">石实<em>SHINE</em><small>数智成长 · 管理端</small></div>
        </div>
        <nav class="nav">
          <template v-for="g in groups" :key="g.label">
            <div class="grp">{{ g.label }}</div>
            <button v-for="t in g.items" :key="t.name" class="nv" type="button"
              :class="{ on: tab === t.name }" @click="tab = t.name">
              <el-icon><component :is="t.icon" /></el-icon>{{ t.label }}
              <i v-if="t.name === 'roleRequest' && pendingCount > 0" class="badge">{{ pendingCount > 99 ? '99+' : pendingCount }}</i>
            </button>
          </template>
        </nav>
        <div class="ft">石实SHINE · v2.0</div>
      </aside>
      <div class="main">
        <header class="top">
          <span class="crumb">首页 / <b>{{ current.label }}</b></span>
          <div v-if="online.users !== null" class="chips">
            <span class="chip">在线 <b>{{ online.onlineCount }}</b></span>
            <span class="chip">24h 活跃 <b>{{ online.dailyCount }}</b></span>
            <el-tooltip v-if="online.users.length" placement="bottom">
              <template #content>
                <div v-for="u in online.users" :key="u.userId">{{ u.realName }}（{{ u.username }}）· {{ ago(u.lastActiveMs) }}</div>
              </template>
              <span class="who">在线名单</span>
            </el-tooltip>
          </div>
          <div class="user">
            <div class="uav">{{ auth.realName?.charAt(0) || '管' }}</div>
            <div><span>{{ auth.realName }}</span><small>{{ roleLabel }}</small></div>
          </div>
        </header>
        <main class="body">
          <div class="pt"><div class="mei"></div><h1>{{ current.label }}</h1></div>
          <component :is="current.comp" @handled="loadPendingCount" />
        </main>
      </div>
    </template>

    <!-- 窄屏 <1024：第一版 tabs 形态（手机/平板管理端不变） -->
    <template v-else>
      <motion.h2 class="page-title" :initial="{ opacity: 0, x: -16 }" :animate="{ opacity: 1, x: 0 }"
        :transition="{ type: 'spring', stiffness: 400, damping: 32 }"><el-icon><Setting /></el-icon>系统管理</motion.h2>
      <div v-if="online.users !== null" class="online-bar">
        <span class="dot"></span>
        当前在线 <b>{{ online.onlineCount }}</b> 人
        <span class="sep">·</span>
        24 小时活跃 <b>{{ online.dailyCount }}</b> 人
        <el-tooltip v-if="online.users.length" placement="bottom">
          <template #content>
            <div v-for="u in online.users" :key="u.userId">{{ u.realName }}（{{ u.username }}）· {{ ago(u.lastActiveMs) }}</div>
          </template>
          <span class="who">在线名单</span>
        </el-tooltip>
      </div>
      <el-tabs v-model="tab">
        <el-tab-pane label="教师与任课" name="teacher"><TeacherTab /></el-tab-pane>
        <el-tab-pane label="家长账号" name="parent"><ParentTab /></el-tab-pane>
        <el-tab-pane :label="`账号审批${pendingCount ? '(' + pendingCount + ')' : ''}`" name="roleRequest"><RoleRequestTab @handled="loadPendingCount" /></el-tab-pane>
        <el-tab-pane label="内容发布" name="content"><ContentTab /></el-tab-pane>
        <el-tab-pane label="教师档案" name="teacherProfile"><TeacherProfileTab /></el-tab-pane>
        <el-tab-pane label="年级与班级" name="org"><OrgTab /></el-tab-pane>
        <el-tab-pane label="学生" name="student"><StudentTab /></el-tab-pane>
        <el-tab-pane label="学期" name="term"><TermTab /></el-tab-pane>
        <el-tab-pane label="考试管理" name="exam"><ExamTab /></el-tab-pane>
        <el-tab-pane label="值班排班" name="duty"><DutyTab /></el-tab-pane>
        <el-tab-pane label="育人指标" name="indicator"><IndicatorTab /></el-tab-pane>
        <el-tab-pane label="成长银行" name="shop"><ShopTab /></el-tab-pane>
        <el-tab-pane label="报告模板" name="template"><TemplateTab /></el-tab-pane>
        <el-tab-pane label="审计日志" name="audit"><AuditTab /></el-tab-pane>
        <el-tab-pane label="AI 用量" name="aiUsage"><AiUsageTab /></el-tab-pane>
        <el-tab-pane label="版本更新" name="appRelease"><AppReleaseTab /></el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { motion } from 'motion-v'
import { Aim, AlarmClock, Calendar, Coin, DataLine, Document, Iphone, Postcard, Promotion, School, Setting, Stamp, Tickets, TrendCharts, Upload, User, Avatar } from '@element-plus/icons-vue'
import { api } from '../api/http'
import { useAuthStore } from '../stores/auth'
import TeacherTab from '../components/admin/TeacherTab.vue'
import ParentTab from '../components/admin/ParentTab.vue'
import RoleRequestTab from '../components/admin/RoleRequestTab.vue'
import ContentTab from '../components/admin/ContentTab.vue'
import TeacherProfileTab from '../components/admin/TeacherProfileTab.vue'
import OrgTab from '../components/admin/OrgTab.vue'
import StudentTab from '../components/admin/StudentTab.vue'
import TermTab from '../components/admin/TermTab.vue'
import ExamTab from '../components/admin/ExamTab.vue'
import DutyTab from '../components/admin/DutyTab.vue'
import IndicatorTab from '../components/admin/IndicatorTab.vue'
import ShopTab from '../components/admin/ShopTab.vue'
import TemplateTab from '../components/admin/TemplateTab.vue'
import AuditTab from '../components/admin/AuditTab.vue'
import AiUsageTab from '../components/admin/AiUsageTab.vue'
import AppReleaseTab from '../components/admin/AppReleaseTab.vue'

const auth = useAuthStore()

/* 支持 ?tab= 直达指定页签（首页快捷功能「教师档案」入口用）；已打开时 query 变化也跟随 */
const route = useRoute()
const tab = ref((route.query.tab as string) || 'teacher')
watch(() => route.query.tab, (t) => { if (typeof t === 'string' && t !== tab.value) tab.value = t })

/* 桌面 ≥1024 切侧栏形态（与 style.css 三档断点一致；App 壳同条件隐藏返回条） */
const desktop = ref(window.matchMedia('(min-width: 1024px)').matches)
const mq = window.matchMedia('(min-width: 1024px)')
const onMq = (e: MediaQueryListEvent) => { desktop.value = e.matches }

/* 侧栏导航：12 个页签分四组；comp=markRaw 防组件被响应式代理 */
const TABS: Record<string, { label: string; comp: any; icon: any }> = {
  teacher: { label: '教师与任课', comp: markRaw(TeacherTab), icon: User },
  parent: { label: '家长账号', comp: markRaw(ParentTab), icon: Iphone },
  roleRequest: { label: '账号审批', comp: markRaw(RoleRequestTab), icon: Stamp },
  teacherProfile: { label: '教师档案', comp: markRaw(TeacherProfileTab), icon: Postcard },
  content: { label: '内容发布', comp: markRaw(ContentTab), icon: Promotion },
  appRelease: { label: '版本更新', comp: markRaw(AppReleaseTab), icon: Upload },
  org: { label: '年级与班级', comp: markRaw(OrgTab), icon: School },
  student: { label: '学生', comp: markRaw(StudentTab), icon: Avatar },
  term: { label: '学期', comp: markRaw(TermTab), icon: Calendar },
  exam: { label: '考试管理', comp: markRaw(ExamTab), icon: TrendCharts },
  duty: { label: '值班排班', comp: markRaw(DutyTab), icon: AlarmClock },
  indicator: { label: '育人指标', comp: markRaw(IndicatorTab), icon: Aim },
  shop: { label: '成长银行', comp: markRaw(ShopTab), icon: Coin },
  template: { label: '报告模板', comp: markRaw(TemplateTab), icon: Document },
  audit: { label: '审计日志', comp: markRaw(AuditTab), icon: Tickets },
  aiUsage: { label: 'AI 用量', comp: markRaw(AiUsageTab), icon: DataLine },
}
const groups = [
  { label: '账号与人员', items: ['teacher', 'parent', 'roleRequest', 'teacherProfile'] },
  { label: '内容运营', items: ['content', 'appRelease'] },
  { label: '基础数据', items: ['org', 'student', 'term', 'exam', 'duty', 'indicator', 'shop', 'template'] },
  { label: '系统运维', items: ['audit', 'aiUsage'] },
].map((g) => ({ ...g, items: g.items.map((k) => ({ name: k, ...TABS[k] })) }))

const current = computed(() => TABS[tab.value] ?? TABS.teacher)

const roleLabel = computed(() =>
  auth.role === 'ADMIN' ? '管理员' : auth.role === 'LEADER' ? '领导' : '教师')

/* 待审批徽标（批2-5）：与在线统计同轮询刷新；RoleRequestTab 处理完即时回调 */
const pendingCount = ref(0)
async function loadPendingCount() {
  try {
    const d = await api<{ pending: number }>('/api/role-request/count')
    pendingCount.value = d.pending
  } catch { /* 静默（非审批角色不显示） */ }
}

/* 在线统计：桌面顶栏 chips / 窄屏统计条共用；30 秒轮询，失败静默 */
const online = reactive<{ onlineCount: number; dailyCount: number; users: any[] | null }>({
  onlineCount: 0, dailyCount: 0, users: null,
})
let onlineTimer: number | undefined

async function loadOnline() {
  try {
    const d = await api<{ onlineCount: number; dailyCount: number; onlineUsers: any[] }>('/api/admin/online')
    online.onlineCount = d.onlineCount
    online.dailyCount = d.dailyCount
    online.users = d.onlineUsers
  } catch { /* 静默 */ }
}

function ago(ms: number) {
  const s = Math.max(0, Math.round((Date.now() - ms) / 1000))
  return s < 60 ? `${s} 秒前活跃` : `${Math.round(s / 60)} 分钟前活跃`
}

onMounted(() => {
  loadOnline()
  loadPendingCount()
  onlineTimer = window.setInterval(() => { loadOnline(); loadPendingCount() }, 30_000)
  mq.addEventListener('change', onMq)
})
onUnmounted(() => {
  window.clearInterval(onlineTimer)
  mq.removeEventListener('change', onMq)
})
</script>

<style scoped>
.online-bar { display: flex; align-items: center; gap: 8px; margin: 0 0 14px; font-size: 13px; color: var(--el-text-color-secondary); }
.online-bar b { color: var(--el-color-primary); font-size: 15px; }
.online-bar .sep { color: var(--el-border-color-darker); }
.online-bar .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--el-color-success); animation: online-pulse 2s infinite; }
.who { cursor: help; text-decoration: underline dotted; font-size: 12px; color: var(--el-text-color-secondary); }
@keyframes online-pulse {
  0% { box-shadow: 0 0 0 0 rgba(103, 194, 58, 0.45); }
  70% { box-shadow: 0 0 0 7px rgba(103, 194, 58, 0); }
  100% { box-shadow: 0 0 0 0 rgba(103, 194, 58, 0); }
}
:deep(.el-tabs__header) { margin: 0 0 16px; }
:deep(.el-tabs__nav-wrap::after) { height: 1px; background: var(--el-border-color-lighter); }
:deep(.el-tabs__item) { font-size: 15px; }

/* ────────── 桌面侧栏形态（≥1024；配色=§11 C 令牌，白底现代网格+红 active 金侧边） ────────── */
.admin-desktop { display: flex; max-width: none; padding: 0; height: 100%; background: #FBFBFD; }

.aside { flex: none; width: 222px; background: var(--shine-card); border-right: 1px solid #EDEFF4;
  display: flex; flex-direction: column; }
.logo { display: flex; align-items: center; gap: 10px; padding: 20px 20px 18px; }
.logo img { width: 36px; height: 36px; object-fit: contain; }
.lb { font-size: 17px; font-weight: 800; color: var(--shine-navy); letter-spacing: 1px; }
.lb em { font-style: normal; color: var(--shine-red); }
.lb small { display: block; font-size: 9px; color: #8A93A6; letter-spacing: 2.5px; font-weight: 500; margin-top: 1px; }
.nav { padding: 6px 12px; flex: 1; overflow-y: auto; }
.grp { padding: 12px 12px 6px; font-size: 10.5px; color: #A0A7B8; letter-spacing: 2px; font-weight: 700; }
.nv { display: flex; align-items: center; gap: 10px; width: 100%; padding: 10.5px 12px; border: none;
  border-radius: 9px; background: none; color: var(--shine-navy-soft); font-size: 13px; font-weight: 600;
  margin-bottom: 3px; position: relative; cursor: pointer; text-align: left; }
.nv .el-icon { font-size: 17px; }
.nv:hover { background: #F6F7FA; }
.nv.on { background: var(--shine-red-soft); color: var(--shine-red); }
.nv.on::before { content: ""; position: absolute; left: -12px; top: 8px; bottom: 8px; width: 3px;
  background: linear-gradient(180deg, var(--shine-red), var(--shine-gold)); border-radius: 0 3px 3px 0; }
.badge { margin-left: auto; font-style: normal; font-size: 10px; font-weight: 700; line-height: 1;
  color: #fff; background: var(--shine-red); border-radius: 8px; padding: 3px 6px; }
.ft { padding: 14px 20px; border-top: 1px solid #EDEFF4; font-size: 10.5px; color: #8A93A6; letter-spacing: 1px; }

.main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.top { flex: none; height: 62px; background: var(--shine-card); border-bottom: 1px solid #EDEFF4;
  display: flex; align-items: center; padding: 0 26px; gap: 14px; }
.crumb { font-size: 13px; color: #8A93A6; }
.crumb b { color: var(--shine-navy); font-weight: 700; }
.chips { margin-left: auto; display: flex; gap: 9px; align-items: center; }
.chip { background: #F4F6FA; border-radius: 8px; padding: 6px 12px; font-size: 12px; color: var(--shine-navy-soft); }
.chip b { font-weight: 800; color: var(--shine-navy); }
.user { display: flex; align-items: center; gap: 9px; margin-left: 6px; }
.uav { width: 32px; height: 32px; border-radius: 50%; background: var(--shine-navy); color: #E8CE8C;
  display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; }
.user span { font-size: 12.5px; color: var(--shine-navy); font-weight: 700; }
.user small { display: block; font-size: 10px; color: #8A93A6; font-weight: 400; }

.body { flex: 1; overflow-y: auto; padding: 22px 26px 32px; }
.pt { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.pt .mei { width: 22px; height: 9px;
  background: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='22' height='9'%3E%3Cpath d='M1 1h7v7M21 1h-7v7' fill='none' stroke='%23C9A227' stroke-width='1.4'/%3E%3C/svg%3E") no-repeat; }
.pt h1 { margin: 0; font-family: 'Noto Serif SC', 'Source Han Serif SC', 'STZhongsong', 'SimSun', serif;
  font-size: 21px; color: var(--shine-navy); font-weight: 700; letter-spacing: 2px; }
</style>
