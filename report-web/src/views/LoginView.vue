<template>
  <div class="login-wrap" @mousemove="onMove">
    <!-- 光斑视差：外层 motion 管鼠标跟随（spring），内层 .deco 保留 floaty 浮动，两层 transform 互不覆盖 -->
    <motion.div class="deco-p" aria-hidden="true" :style="{ x: pxA, y: pyA }"><div class="deco deco-a"></div></motion.div>
    <motion.div class="deco-p" aria-hidden="true" :style="{ x: pxB, y: pyB }"><div class="deco deco-b"></div></motion.div>
    <!-- spring 物理入场（替代原 CSS card-in，MotionConfig 感知系统减弱动效） -->
    <div class="login-split">
    <!-- 宽屏（≥900px）左侧校园照片面板：校门 + 校名 + 校训；窄屏隐藏（手机/平板兼容） -->
    <aside class="login-side" aria-hidden="true">
      <img class="side-badge" src="/badge.png" alt="">
      <div class="side-text">
        <div class="side-name">佛山市南海区石实实验学校</div>
        <div class="side-motto">任重道远 · 毋忘奋斗</div>
        <div class="side-en">SHISHI EXPERIMENTAL SCHOOL · EST. 1999</div>
      </div>
    </aside>
    <motion.div class="login-card-motion"
      :initial="{ opacity: 0, y: 30, scale: 0.96 }" :animate="{ opacity: 1, y: 0, scale: 1 }"
      :transition="{ type: 'spring', stiffness: 240, damping: 22 }">
    <el-card class="login-card" shadow="never">
      <div class="login-head">
        <img class="login-logo" src="/badge.png" alt="">
        <h2>石实实验学校 · 中学素质报告平台</h2>
        <p>任重道远，毋忘奋斗</p>
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
    radial-gradient(720px 420px at 88% -8%, rgba(201,138,45,.38), transparent 62%),
    radial-gradient(640px 480px at -6% 108%, rgba(0,120,212,.45), transparent 60%),
    linear-gradient(135deg, #3a0d10 0%, #6e151c 42%, #a8232b 100%);
}
.deco-p { pointer-events: none; }
.deco { position: absolute; border-radius: 50%; filter: blur(56px); opacity: .5; pointer-events: none; }
.deco-a { width: 260px; height: 260px; right: -60px; top: 12%; background: rgba(217,142,35,.42); animation: floaty 7s ease-in-out infinite; }
.deco-b { width: 220px; height: 220px; left: -50px; bottom: 8%; background: rgba(83,168,255,.45); animation: floaty 9s ease-in-out infinite reverse; }
.login-split { display: flex; align-items: stretch; justify-content: center; width: 100%; }
.login-card-motion { width: 100%; max-width: 380px; }
.login-card { width: 100%; max-width: 380px; border: 1px solid rgba(255,255,255,.5); border-radius: 12px;
  background: rgba(255,255,255,.94); backdrop-filter: blur(14px);
  box-shadow: 0 12px 40px rgba(58,13,16,.3); }
.login-head { text-align: center; margin-bottom: 18px; }
.login-logo { display: inline-block; width: 46px; height: auto; margin-top: 4px; }
.login-side { display: none; }
@media (min-width: 900px) {
  .login-side {
    display: flex; flex-direction: column; justify-content: space-between;
    width: 340px; flex: none; border-radius: 12px 0 0 12px; overflow: hidden;
    background: linear-gradient(180deg, rgba(58,13,16,.08) 30%, rgba(58,13,16,.82) 100%),
                url('/gate.jpg') center 38%/cover no-repeat;
    box-shadow: 0 12px 40px rgba(58,13,16,.3);
  }
  .side-badge { width: 72px; margin: 24px; padding: 8px; border-radius: 10px; background: rgba(255,255,255,.88); }
  .side-text { padding: 24px 26px; color: #fff; text-shadow: 0 1px 6px rgba(58,13,16,.6); }
  .side-name { font-size: 19px; font-weight: 700; letter-spacing: 1px; }
  .side-motto { margin-top: 8px; font-size: 14px; color: #f3d9a5; letter-spacing: 3px; }
  .side-en { margin-top: 4px; font-size: 10px; color: #e9c3c6; letter-spacing: 1.5px; }
  .login-card { border-radius: 0 12px 12px 0; border-left: none; }
}
.login-head h2 { margin: 12px 0 4px; font-size: 19px; color: #1f2937; }
.login-head p { margin: 0; font-size: 13px; color: #6b7280; letter-spacing: 1px; }
.login-btn { width: 100%; height: 40px; font-size: 15px; letter-spacing: 6px; border-radius: 10px; }
@keyframes floaty { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-14px); } }
</style>
