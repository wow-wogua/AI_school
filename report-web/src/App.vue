<template>
  <MotionConfig reducedMotion="user">
  <el-container style="height: 100%">
    <el-header class="nav" style="height: auto">
      <div class="nav-inner">
        <span class="brand">
          <motion.span class="brand-mark" aria-hidden="true"
            :initial="{ scale: 0, rotate: -60 }" :animate="{ scale: 1, rotate: 0 }"
            :whileHover="{ rotate: -14, scale: 1.12 }"
            :transition="{ type: 'spring', stiffness: 300, damping: 15 }">
            <!-- 内联 SVG 幼苗（无文本节点，不影响 E2E 断言；无图片资源） -->
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
                 stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M7 20h10"/><path d="M10 20c5.5-2.5.8-6.4 3-10"/>
              <path d="M9.5 9.4c1.1.8 1.8 2.2 2.3 3.7-2 .4-3.5.4-4.8-.3-1.2-.6-2.3-1.9-3-4.2 2.8-.5 4.4 0 5.5.8z"/>
              <path d="M14.1 6a7 7 0 0 0-1.1 4c1.9-.1 3.3-.6 4.3-1.4 1-1 1.6-2.3 1.7-4.6-2.7.1-4 1-4.9 2z"/>
            </svg>
          </motion.span>
          <span class="brand-text">数智成长<i>初中素质报告平台</i></span>
        </span>
        <nav v-if="auth.token" class="links">
          <router-link to="/">批量任务</router-link>
          <router-link to="/reports">报告列表</router-link>
          <router-link to="/scores">成绩管理</router-link>
          <router-link to="/evaluate">日常评价</router-link>
          <router-link to="/summary">成长总结</router-link>
          <router-link to="/comprehensive">综合素质</router-link>
          <router-link to="/comments">班主任寄语</router-link>
          <router-link to="/activity">活动管理</router-link>
          <router-link to="/honor">荣誉证书</router-link>
          <router-link to="/timeline">成长时间轴</router-link>
          <router-link v-if="auth.role === 'ADMIN'" to="/admin">系统管理</router-link>
        </nav>
        <span v-if="auth.token" class="user">
          {{ auth.realName }}
          <el-button link size="small" @click="logout">退出</el-button>
        </span>
      </div>
      <!-- 滚动进度条：阅读位置反馈（useScroll 联动主滚动区，spring 平滑） -->
      <motion.div class="scroll-progress" aria-hidden="true" :style="{ scaleX: progress }" />
    </el-header>
    <el-main ref="mainRef" style="padding: 0; min-height: 0">
      <router-view v-slot="{ Component }">
        <transition name="page-fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </el-main>
  </el-container>
  </MotionConfig>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { MotionConfig, motion, useScroll, useSpring } from 'motion-v'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const router = useRouter()
function logout() {
  auth.logout()
  router.push('/login')
}

/* 滚动进度条：el-main 是页面真正的滚动容器（overflow:auto），取其实例根元素 */
const mainRef = ref<{ $el?: HTMLElement }>()
const { scrollYProgress } = useScroll({ container: computed(() => mainRef.value?.$el) })
const progress = useSpring(scrollYProgress, { stiffness: 140, damping: 30, mass: 0.4 })
</script>

<style scoped>
.nav {
  position: sticky; top: 0; z-index: 100;
  background: linear-gradient(100deg, #1e1b4b 0%, #312e81 45%, #4338ca 100%);
  box-shadow: 0 2px 12px rgba(30,27,75,.25);
}
.nav-inner { display: flex; align-items: center; gap: 20px; padding: 12px 16px; max-width: 1200px; margin: 0 auto; flex-wrap: wrap; }
.brand { display: flex; align-items: center; gap: 10px; color: #fff; }
.brand-mark { display: inline-flex; width: 30px; height: 30px; align-items: center; justify-content: center;
  border-radius: 9px; color: #fff; background: var(--brand-gradient); box-shadow: 0 4px 10px rgba(16,185,129,.35); }
.brand-text { display: flex; flex-direction: column; line-height: 1.2; font-weight: 700; font-size: 16px; letter-spacing: .5px; }
.brand-text i { font-style: normal; font-size: 11px; font-weight: 400; color: #a5b4fc; letter-spacing: 2px; }
.links { display: flex; gap: 4px; flex-wrap: wrap; flex: 1; }
.links a { position: relative; color: #c7d2fe; text-decoration: none; font-size: 14px; padding: 6px 10px;
  border-radius: 8px; transition: color .2s ease, background-color .2s ease; }
.links a::after { content: ""; position: absolute; left: 10px; right: 10px; bottom: 2px; height: 2px; border-radius: 2px;
  background: var(--brand-gradient-bar); transform: scaleX(0); transform-origin: left center; transition: transform .22s ease; }
.links a:hover { color: #fff; }
.links a.router-link-exact-active { color: #fff; }
.links a.router-link-exact-active::after { transform: scaleX(1); }
.user { color: #c7d2fe; font-size: 14px; display: flex; align-items: center; gap: 4px; }
.scroll-progress { position: absolute; left: 0; right: 0; bottom: 0; height: 2px;
  background: var(--brand-gradient-bar); transform-origin: 0 50%; }
.user :deep(.el-button) { color: #a5b4fc; }
.user :deep(.el-button:hover) { color: #fff; }
@media (max-width: 767px) {
  .brand-text { font-size: 14px; }
  .links { order: 3; width: 100%; }
  .links a { padding: 4px 9px; background: rgba(255,255,255,.08); border-radius: 999px; font-size: 13px; }
  .links a.router-link-exact-active { background: rgba(255,255,255,.2); }
  .links a::after { display: none; }   /* 手机上用胶囊态代替下划线 */
}
</style>
