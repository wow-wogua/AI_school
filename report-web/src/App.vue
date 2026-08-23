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
            <!-- 石实校徽（白底圆角衬深色导航） -->
            <img src="/badge.png" alt="" style="width: 22px">
          </motion.span>
          <span class="brand-text">石实实验学校<i>中学素质报告平台</i></span>
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
          <AiTaskPanel />
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
      <!-- 石实页脚：全站主界面统一校名/校训/理念 -->
      <footer v-if="auth.token" class="app-footer">
        <img src="/badge.png" alt="" class="footer-badge">
        <span class="footer-line">
          <b>佛山市南海区石实实验学校</b>
          <i>校训 任重道远，毋忘奋斗</i>
          <i>扬长教育，出彩人生</i>
          <i>EST. 1999</i>
        </span>
      </footer>
    </el-main>
  </el-container>
  </MotionConfig>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { MotionConfig, motion, useScroll, useSpring } from 'motion-v'
import { useAuthStore } from './stores/auth'
import { useAiTasksStore } from './stores/aiTasks'
import AiTaskPanel from './components/AiTaskPanel.vue'

const auth = useAuthStore()
const aiTasks = useAiTasksStore()
const router = useRouter()
function logout() {
  auth.logout()
  router.push('/login')
}

/* AI 任务轮询随登录态启停（登录即恢复展示后台跑的任务，退出即停并清空） */
watch(() => auth.token, (t) => (t ? aiTasks.start() : aiTasks.stop()), { immediate: true })

/* 滚动进度条：el-main 是页面真正的滚动容器（overflow:auto），取其实例根元素 */
const mainRef = ref<{ $el?: HTMLElement }>()
const { scrollYProgress } = useScroll({ container: computed(() => mainRef.value?.$el) })
const progress = useSpring(scrollYProgress, { stiffness: 140, damping: 30, mass: 0.4 })
</script>

<style scoped>
.nav {
  position: sticky; top: 0; z-index: 100;
  background: #1e1b4b;
  box-shadow: 0 2px 8px rgba(30,27,75,.15);
}
.nav-inner { display: flex; align-items: center; gap: 20px; padding: 12px 16px; max-width: 1200px; margin: 0 auto; flex-wrap: wrap; }
.brand { display: flex; align-items: center; gap: 10px; color: #fff; }
.brand-mark { display: inline-flex; width: 30px; height: 30px; align-items: center; justify-content: center;
  border-radius: 9px; color: #fff; background: #fff; box-shadow: 0 2px 6px rgba(30,27,75,.3); }
.brand-text { display: flex; flex-direction: column; line-height: 1.2; font-weight: 700; font-size: 16px; letter-spacing: .5px; }
.brand-text i { font-style: normal; font-size: 11px; font-weight: 400; color: #a5b4fc; letter-spacing: 2px; }
.links { display: flex; gap: 4px; flex-wrap: wrap; flex: 1; }
.links a { position: relative; color: #c7d2fe; text-decoration: none; font-size: 14px; padding: 6px 10px;
  border-radius: 8px; transition: color .2s ease, background-color .2s ease; }
.links a::after { content: ""; position: absolute; left: 10px; right: 10px; bottom: 2px; height: 2px; border-radius: 2px;
  background: #818cf8; transform: scaleX(0); transform-origin: left center; transition: transform .22s ease; }
.links a:hover { color: #fff; }
.links a.router-link-exact-active { color: #fff; }
.links a.router-link-exact-active::after { transform: scaleX(1); }
.user { color: #c7d2fe; font-size: 14px; display: flex; align-items: center; gap: 4px; }
.scroll-progress { position: absolute; left: 0; right: 0; bottom: 0; height: 2px;
  background: var(--brand-teal); transform-origin: 0 50%; }
.user :deep(.el-button) { color: #a5b4fc; }
.user :deep(.el-button:hover) { color: #fff; }
.app-footer { display: flex; align-items: center; justify-content: center; gap: 10px;
  padding: 20px 16px 26px; margin-top: 28px; color: #94a3b8; font-size: 12px; letter-spacing: .5px; }
.app-footer b { color: #475569; font-weight: 600; white-space: nowrap; }
.app-footer i { font-style: normal; white-space: nowrap; }
.footer-badge { width: 18px; height: auto; opacity: .85; flex: none; }
.app-footer .footer-line { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; justify-content: center; }
@media (max-width: 767px) {
  .brand-text { font-size: 14px; }
  .links { order: 3; width: 100%; }
  .links a { padding: 4px 9px; background: rgba(255,255,255,.08); border-radius: 999px; font-size: 13px; }
  .links a.router-link-exact-active { background: rgba(255,255,255,.2); }
  .links a::after { display: none; }   /* 手机上用胶囊态代替下划线 */
}
</style>
