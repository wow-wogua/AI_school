<template>
  <div class="login-wrap">
    <div class="login-bg" aria-hidden="true"></div>
    <motion.div class="login-card"
      :initial="{ opacity: 0, y: 30, scale: 0.96 }" :animate="{ opacity: 1, y: 0, scale: 1 }"
      :transition="{ type: 'spring', stiffness: 240, damping: 22 }">
      <div class="login-head">
        <img class="login-logo" src="/badge.png" alt="">
        <h1>修改初始密码</h1>
        <p>当前密码由管理员设置，为保障账号安全，首次使用请先设置你自己的密码</p>
      </div>

      <van-form @submit="doChange">
        <van-cell-group inset :border="false" class="fields">
          <van-field v-model="form.old" type="password" label-width="76px" left-icon="lock" placeholder="当前密码（管理员下发）"
            :rules="[{ required: true, message: '请输入当前密码' }]" />
          <van-field v-model="form.next" type="password" label-width="76px" left-icon="edit" placeholder="新密码（至少 8 位）"
            :rules="[{ required: true, message: '请输入新密码' }, { validator: (v: string) => v.length >= 8, message: '新密码至少 8 位' }]" />
          <van-field v-model="form.again" type="password" label-width="76px" left-icon="edit" placeholder="再输入一遍新密码"
            :rules="[{ required: true, message: '请再次输入新密码' }, { validator: (v: string) => v === form.next, message: '两次输入不一致' }]" />
        </van-cell-group>
        <van-button round block type="primary" native-type="submit" class="login-btn" :loading="loading">
          保存并进入系统
        </van-button>
      </van-form>
      <button class="srv-toggle" type="button" @click="doLogout">用其他账号登录</button>
    </motion.div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { motion } from 'motion-v'
import { showSuccessToast, showFailToast } from 'vant'
import { api } from '../api/http'
import { useAuthStore } from '../stores/auth'

const form = reactive({ old: '', next: '', again: '' })
const loading = ref(false)
const router = useRouter()
const auth = useAuthStore()

async function doChange() {
  loading.value = true
  try {
    const data = await api<{ token: string }>('/api/auth/password', {
      method: 'PUT',
      json: { oldPassword: form.old, newPassword: form.next },
    })
    auth.refreshToken(data.token, true)
    showSuccessToast('密码已修改')
    router.replace('/')
  } catch (e) {
    const msg = (e as Error).message || ''
    if (msg.includes('旧密码')) showFailToast('当前密码不正确')
    else if (msg.includes('8 位')) showFailToast('新密码至少 8 位')
    else showFailToast(msg || '修改失败，请重试')
  } finally {
    loading.value = false
  }
}

function doLogout() {
  auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
.login-wrap {
  position: relative; height: 100%; height: 100dvh; overflow: hidden;
  display: flex; align-items: center; justify-content: center; padding: 24px;
  background: #101B3D; /* 图片加载前兜底 */
}
.login-bg {
  position: absolute; inset: -14px;
  background:
    radial-gradient(720px 420px at 85% -10%, rgba(91,133,232,.3), transparent 62%),
    radial-gradient(640px 480px at -6% 108%, rgba(201,138,45,.18), transparent 60%),
    linear-gradient(160deg, rgba(13,22,50,.72) 0%, rgba(22,38,90,.52) 45%, rgba(30,58,138,.64) 100%),
    url('/campus-bg.jpg') center 42%/cover no-repeat;
  filter: blur(2px);
}
.login-card {
  position: relative; width: 100%; max-width: 380px;
  background: rgba(255,255,255,.94); backdrop-filter: blur(14px);
  border: 1px solid rgba(255,255,255,.5); border-radius: 20px;
  box-shadow: 0 16px 48px rgba(9,18,46,.45);
  padding: 28px 18px 18px;
}
.login-head { text-align: center; margin-bottom: 18px; }
.login-logo { width: 52px; height: auto; }
.login-head h1 {
  margin: 10px 0 4px; font-size: 21px; font-weight: 800; letter-spacing: 2px;
  background: linear-gradient(150deg, #1E3A8A, #2F5FC0);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.login-head p { margin: 0; font-size: 12px; color: var(--app-text-2); line-height: 18px; padding: 0 8px; }

.fields { margin: 0 0 14px; border-radius: 14px; overflow: hidden;
  border: 1px solid var(--app-card-border); }
:deep(.fields .van-field) { padding: 12px 14px; font-size: 15px; }
:deep(.fields .van-field .van-icon) { color: var(--app-blue); font-size: 18px; }
.login-btn { height: 44px; font-size: 16px; font-weight: 600;
  background: linear-gradient(150deg, #1E3A8A, #2F5FC0); border: none; }

.srv-toggle { display: flex; align-items: center; gap: 6px; justify-content: center;
  width: 100%; margin-top: 16px; padding: 6px; background: none; border: none;
  color: var(--app-text-3); font-size: 12px; cursor: pointer; }
</style>
