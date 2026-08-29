<template>
  <MotionConfig reducedMotion="user">
    <!-- App 形态（tab/keepTab）：主内容区 + 底部导航 -->
    <div v-if="layout === 'tab' || layout === 'keepTab'" class="app-shell">
      <main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
      <AppTabbar :tab="(route.meta.tab as any)" />
    </div>

    <!-- sub 二级/功能页：meta.title 有值挂「返回+标题」导航条；无值（学生详情）仅滚动容器 -->
    <div v-else-if="layout === 'sub'" class="app-sub" :class="{ admin: route.meta.admin }">
      <header v-if="route.meta.title" class="sub-nav">
        <button class="back" type="button" aria-label="返回" @click="goBack">
          <van-icon name="arrow-left" />
        </button>
        <h1>{{ route.meta.title }}</h1>
      </header>
      <main class="sub-main">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>

    <!-- bare：登录页直接渲染 -->
    <router-view v-else />
  </MotionConfig>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MotionConfig } from 'motion-v'
import { useAuthStore } from './stores/auth'
import { useAiTasksStore } from './stores/aiTasks'
import AppTabbar from './components/AppTabbar.vue'

const auth = useAuthStore()
const aiTasks = useAiTasksStore()
const router = useRouter()
const route = useRoute()

/** 布局形态由路由 meta 决定：tab/keepTab → Tab 壳；sub → 返回导航壳；bare → 登录页 */
const layout = computed(() => (route.meta.layout as string | undefined) ?? 'bare')

/** 导航条返回：有上一页则回退，否则（深链直入）回首页 */
function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/')
}

/* AI 任务轮询随登录态启停（登录即恢复展示后台跑的任务，退出即停并清空） */
watch(() => auth.token, (t) => (t ? aiTasks.start() : aiTasks.stop()), { immediate: true })
</script>

<style scoped>
/* App 壳：主滚动区 + 底部导航（固定悬浮，主区留出通行高度） */
.app-shell { height: 100%; height: 100dvh; display: flex; flex-direction: column; background: var(--app-bg); }
.app-main { flex: 1; overflow-y: auto; -webkit-overflow-scrolling: touch; padding-bottom: calc(64px + env(safe-area-inset-bottom)); }

/* sub 壳：深蓝渐变导航条（垫虚化校园底图，与 hero 同语言）+ 滚动主区 */
.app-sub { height: 100%; height: 100dvh; display: flex; flex-direction: column; background: var(--app-bg); }
.sub-nav {
  position: relative; display: flex; align-items: center; gap: 10px; flex: none;
  padding: calc(8px + env(safe-area-inset-top)) 14px 8px;
  background: var(--app-gradient); color: #fff; overflow: hidden;
}
.sub-nav::before {                 /* 虚化校园底图（同 .app-hero） */
  content: ''; position: absolute; inset: -30px;
  background: url('/campus-bg.jpg') center 42%/cover no-repeat;
  opacity: .14; filter: blur(8px) saturate(1.1); pointer-events: none;
}
.sub-nav > * { position: relative; }
.sub-nav .back { display: flex; align-items: center; justify-content: center; width: 32px; height: 32px;
  border: none; border-radius: 50%; background: rgba(255,255,255,.16); color: #fff;
  font-size: 16px; cursor: pointer; }
.sub-nav h1 { margin: 0; font-size: 17px; font-weight: 700; letter-spacing: 1px; }
.sub-main { flex: 1; overflow-y: auto; -webkit-overflow-scrolling: touch; }

/* 功能页过渡期仍用 Element Plus 组件：除管理页外，EP 主色临时对齐 App 深蓝
   （管理端保留石实红体系，见 style.css §1） */
.app-sub:not(.admin) {
  --el-color-primary: #2F5FC0;
  --el-color-primary-light-3: #6D8FD3;
  --el-color-primary-light-5: #97AFE0;
  --el-color-primary-light-7: #C2CFEC;
  --el-color-primary-light-8: #D5DFF2;
  --el-color-primary-light-9: #EAEFF9;
  --el-color-primary-dark-2: #264C9A;
}
</style>
