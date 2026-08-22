<template>
  <div class="login-wrap" @mousemove="onMove">
    <!-- 光斑视差：外层 motion 管鼠标跟随（spring），内层 .deco 保留 floaty 浮动，两层 transform 互不覆盖 -->
    <motion.div class="deco-p" aria-hidden="true" :style="{ x: pxA, y: pyA }"><div class="deco deco-a"></div></motion.div>
    <motion.div class="deco-p" aria-hidden="true" :style="{ x: pxB, y: pyB }"><div class="deco deco-b"></div></motion.div>
    <!-- spring 物理入场（替代原 CSS card-in，MotionConfig 感知系统减弱动效） -->
    <motion.div class="login-card-motion"
      :initial="{ opacity: 0, y: 30, scale: 0.96 }" :animate="{ opacity: 1, y: 0, scale: 1 }"
      :transition="{ type: 'spring', stiffness: 240, damping: 22 }">
    <el-card class="login-card" shadow="never">
      <div class="login-head">
        <span class="login-logo" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor"
               stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M7 20h10"/><path d="M10 20c5.5-2.5.8-6.4 3-10"/>
            <path d="M9.5 9.4c1.1.8 1.8 2.2 2.3 3.7-2 .4-3.5.4-4.8-.3-1.2-.6-2.3-1.9-3-4.2 2.8-.5 4.4 0 5.5.8z"/>
            <path d="M14.1 6a7 7 0 0 0-1.1 4c1.9-.1 3.3-.6 4.3-1.4 1-1 1.6-2.3 1.7-4.6-2.7.1-4 1-4.9 2z"/>
          </svg>
        </span>
        <h2>数智成长 · 初中素质报告平台</h2>
        <p>记录每一步成长，看见每一种精彩</p>
      </div>
      <el-form :model="form" @submit.enter.prevent="doLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" show-password placeholder="密码" @keyup.enter="doLogin" />
        </el-form-item>
        <el-button class="login-btn" type="primary" :loading="loading" @click="doLogin">登录</el-button>
      </el-form>
    </el-card>
    </motion.div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { motion, useMotionValue, useSpring, useTransform } from 'motion-v'
import { api } from '../api/http'
import { useAuthStore } from '../stores/auth'

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const router = useRouter()
const auth = useAuthStore()

/* 光斑视差：鼠标归一化偏移 ±14px → spring 平滑；B 斑反向 0.7 倍漂出层次 */
const mx = useMotionValue(0)
const my = useMotionValue(0)
const pxA = useSpring(mx, { stiffness: 50, damping: 20 })
const pyA = useSpring(my, { stiffness: 50, damping: 20 })
const pxB = useTransform(pxA, v => -v * 0.7)
const pyB = useTransform(pyA, v => -v * 0.7)
function onMove(e: MouseEvent) {
  const r = (e.currentTarget as HTMLElement).getBoundingClientRect()
  mx.set(((e.clientX - r.left) / r.width - 0.5) * 28)
  my.set(((e.clientY - r.top) / r.height - 0.5) * 28)
}

async function doLogin() {
  if (!form.username || !form.password) return
  loading.value = true
  try {
    const data = await api<{ token: string; user: { realName: string; role: string } }>('/api/auth/login', {
      method: 'POST',
      json: { username: form.username, password: form.password },
    })
    auth.set(data.token, data.user.realName, data.user.role)
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  position: relative; overflow: hidden; height: 100%;
  display: flex; align-items: center; justify-content: center; padding: 24px;
  background:
    radial-gradient(720px 420px at 88% -8%, rgba(16,185,129,.35), transparent 62%),
    radial-gradient(640px 480px at -6% 108%, rgba(99,102,241,.5), transparent 60%),
    linear-gradient(135deg, #1e1b4b 0%, #312e81 42%, #4f46e5 100%);
}
.deco-p { pointer-events: none; }
.deco { position: absolute; border-radius: 50%; filter: blur(56px); opacity: .5; pointer-events: none; }
.deco-a { width: 260px; height: 260px; right: -60px; top: 12%; background: rgba(16,185,129,.4); animation: floaty 7s ease-in-out infinite; }
.deco-b { width: 220px; height: 220px; left: -50px; bottom: 8%; background: rgba(129,140,248,.45); animation: floaty 9s ease-in-out infinite reverse; }
.login-card-motion { width: 100%; max-width: 380px; }
.login-card { width: 100%; max-width: 380px; border: none; border-radius: 16px;
  background: rgba(255,255,255,.94); backdrop-filter: blur(14px);
  box-shadow: 0 24px 64px rgba(17,14,60,.45); }
.login-head { text-align: center; margin-bottom: 18px; }
.login-logo { display: inline-flex; width: 44px; height: 44px; align-items: center; justify-content: center;
  border-radius: 12px; color: #fff; background: var(--brand-gradient); box-shadow: 0 8px 18px rgba(79,70,229,.35); }
.login-head h2 { margin: 12px 0 4px; font-size: 19px; color: #1f2937; }
.login-head p { margin: 0; font-size: 13px; color: #6b7280; letter-spacing: 1px; }
.login-btn { width: 100%; height: 40px; font-size: 15px; letter-spacing: 6px; border-radius: 10px; }
@keyframes floaty { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-14px); } }
</style>
