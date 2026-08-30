<template>
  <div class="login-wrap">
    <!-- 虚化校园大图 + 深蓝渐变罩（设计语言：深蓝体系 + 保留校园元素） -->
    <div class="login-bg" aria-hidden="true"></div>
    <motion.div class="login-card"
      :initial="{ opacity: 0, y: 30, scale: 0.96 }" :animate="{ opacity: 1, y: 0, scale: 1 }"
      :transition="{ type: 'spring', stiffness: 240, damping: 22 }">
      <div class="login-head">
        <img class="login-logo" src="/badge.png" alt="">
        <h1>数智成长</h1>
        <p>石实实验学校 · 中学素质报告平台</p>
        <p class="motto">任重道远，毋忘奋斗</p>
      </div>

      <van-form @submit="doLogin">
        <van-cell-group inset :border="false" class="fields">
          <van-field v-model="form.username" label-width="44px" left-icon="manager-o" placeholder="用户名"
            :rules="[{ required: true, message: '请输入用户名' }]" />
          <van-field v-model="form.password" type="password" label-width="44px" left-icon="lock" placeholder="密码"
            :rules="[{ required: true, message: '请输入密码' }]" />
        </van-cell-group>
        <van-button round block type="primary" native-type="submit" class="login-btn" :loading="loading">
          登录
        </van-button>
      </van-form>

      <!-- App 直连服务器地址（打包形态必配；浏览器形态留空走同源） -->
      <button class="srv-toggle" type="button" @click="srvOpen = !srvOpen">
        <van-icon name="setting-o" /> 服务器地址{{ srvBase ? '' : '（未设置）' }}
        <van-icon :name="srvOpen ? 'arrow-up' : 'arrow-down'" />
      </button>
      <div v-if="srvOpen" class="srv-row">
        <van-field v-model="srvInput" placeholder="如 http://192.168.1.10:8080" clearable />
        <van-button size="small" round type="primary" plain @click="saveSrv">保存</van-button>
      </div>
    </motion.div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { motion } from 'motion-v'
import { showSuccessToast, showFailToast } from 'vant'
import { api, apiBase } from '../api/http'
import { useAuthStore } from '../stores/auth'

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const router = useRouter()
const auth = useAuthStore()

/* 服务器地址：App 安装后首次使用在此配置（默认值来自打包注入 VITE_API_BASE） */
const srvOpen = ref(false)
const srvBase = ref(apiBase())
const srvInput = ref(apiBase())
function saveSrv() {
  const v = srvInput.value.trim().replace(/\/+$/, '')
  if (v) localStorage.setItem('serverBase', v)
  else localStorage.removeItem('serverBase')
  srvBase.value = v
  showSuccessToast(v ? '服务器地址已保存' : '已恢复默认地址')
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
  } catch (e) {
    // 按 HTTP 状态区分失败原因，绝不把"密码错/限流"误报成"连不上服务器"
    // （登录接口的 401 是密码错，与"会话过期"同码不同义——登录页必须自己解释）
    const st = (e as { status?: number }).status
    if (st === 401) showFailToast('用户名或密码错误')
    else if (st === 429) showFailToast('尝试过于频繁，请等 10 分钟再试')
    else showFailToast('连接服务器失败，请检查网络或服务器地址')
  } finally {
    loading.value = false
  }
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
  margin: 10px 0 4px; font-size: 24px; font-weight: 800; letter-spacing: 4px;
  background: linear-gradient(150deg, #1E3A8A, #2F5FC0);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.login-head p { margin: 0; font-size: 12px; color: var(--app-text-2); letter-spacing: 1px; }
.login-head .motto { margin-top: 6px; color: var(--app-gold); letter-spacing: 3px; }

.fields { margin: 0 0 14px; border-radius: 14px; overflow: hidden;
  border: 1px solid var(--app-card-border); }
:deep(.fields .van-field) { padding: 12px 14px; font-size: 15px; }
:deep(.fields .van-field .van-icon) { color: var(--app-blue); font-size: 18px; }
.login-btn { height: 44px; font-size: 16px; font-weight: 600;
  background: linear-gradient(150deg, #1E3A8A, #2F5FC0); border: none; }
.login-btn .van-button__text { letter-spacing: 6px; text-indent: 6px; }  /* 字距用 CSS，可访问名保持「登录」 */

.srv-toggle { display: flex; align-items: center; gap: 6px; justify-content: center;
  width: 100%; margin-top: 16px; padding: 6px; background: none; border: none;
  color: var(--app-text-3); font-size: 12px; cursor: pointer; }
.srv-row { display: flex; gap: 8px; align-items: center; margin-top: 8px; }
.srv-row .van-field { flex: 1; border: 1px solid var(--app-card-border); border-radius: 10px; }
</style>
