<template>
  <div class="app-page mine">
    <!-- 头区：教师信息卡（教师档案阶段3接入编辑入口）；右上校徽水印 -->
    <div class="app-hero hero mark">
      <div class="me">
        <span class="ava">{{ avatarChar }}</span>
        <div>
          <h1>{{ auth.realName }}</h1>
          <p><span class="app-chip role-chip">{{ roleLabel }}</span></p>
        </div>
      </div>
    </div>

    <div class="app-card overlap tl tex-f cells">
      <van-cell title="教师档案" icon="contact" is-link :value="profileHint || '待完善'"
        @click="$router.push('/profile')" />
      <van-cell title="成长报告" icon="orders-o" is-link @click="$router.push('/reports')" />
      <van-cell title="生成中心" icon="bell" is-link :value="running ? `${running} 进行中` : ''" @click="$router.push('/notice')" />
      <van-cell v-if="auth.role === 'ADMIN'" title="系统管理" icon="setting-o" is-link @click="$router.push('/admin')" />
    </div>

    <div class="app-card tex-e cells">
      <van-cell title="修改密码" icon="lock" is-link @click="pwdOpen = true" />
      <van-cell title="服务器地址" icon="desktop-o" is-link :value="srvBase || '默认'" @click="srvOpen = true" />
      <van-cell title="关于" icon="info-o" is-link @click="aboutOpen = true" />
      <van-cell title="退出登录" icon="revoke" is-link class="logout" @click="logoutOpen = true" />
    </div>

    <!-- 修改密码 -->
    <van-dialog v-model:show="pwdOpen" title="修改密码" show-cancel-button :before-close="onPwdClose">
      <div style="padding-top: 10px">
        <van-field v-model="pwd.old" type="password" label="旧密码" placeholder="当前密码" />
        <van-field v-model="pwd.next" type="password" label="新密码" placeholder="至少 6 位" />
        <van-field v-model="pwd.again" type="password" label="确认新密码" placeholder="再输入一遍" />
      </div>
    </van-dialog>

    <!-- 服务器地址编辑 -->
    <van-dialog v-model:show="srvOpen" title="服务器地址" show-cancel-button @confirm="saveSrv">
      <div class="srv-tip">App 直连的学校服务器，如 http://192.168.1.10:8080；留空恢复默认</div>
      <van-field v-model="srvInput" placeholder="http://ip:端口" clearable />
    </van-dialog>

    <!-- 关于 -->
    <van-dialog v-model:show="aboutOpen" title="关于" :show-confirm-button="false">
      <div class="about">
        <img src="/campus-pano.jpg" alt="石实实验学校" class="about-pano">
        <img src="/badge.png" alt="" class="about-badge">
        <b>佛山市南海区石实实验学校</b>
        <p>数智成长 · 中学素质报告平台</p>
        <p class="motto">任重道远，毋忘奋斗 · 扬长教育，出彩人生</p>
        <p class="ver">v{{ version }}</p>
      </div>
    </van-dialog>

    <!-- 退出确认 -->
    <van-dialog v-model:show="logoutOpen" title="退出登录" message="确定退出当前账号吗？" show-cancel-button @confirm="logout" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { useAuthStore } from '../stores/auth'
import { useAiTasksStore } from '../stores/aiTasks'
import { api, apiBase } from '../api/http'

const auth = useAuthStore()
const aiTasks = useAiTasksStore()
const router = useRouter()

const running = computed(() => aiTasks.runningCount)
const avatarChar = computed(() => auth.realName?.charAt(0) || '师')
const roleLabel = computed(() => ({ ADMIN: '管理员', HEAD_TEACHER: '班主任', TEACHER: '任课教师' }[auth.role] ?? auth.role))

const srvOpen = ref(false)
const srvBase = ref(apiBase())
const srvInput = ref(apiBase())
function saveSrv() {
  const v = srvInput.value.trim().replace(/\/+$/, '')
  if (v) localStorage.setItem('serverBase', v)
  else localStorage.removeItem('serverBase')
  srvBase.value = v
  showSuccessToast(v ? '已保存，重新进入页面生效' : '已恢复默认')
}

const aboutOpen = ref(false)

const pwdOpen = ref(false)
const pwd = ref({ old: '', next: '', again: '' })
async function onPwdClose(action: string) {
  if (action !== 'confirm') return true
  if (!pwd.value.old || !pwd.value.next) { showFailToast('请填写完整'); return false }
  if (pwd.value.next.length < 6) { showFailToast('新密码至少 6 位'); return false }
  if (pwd.value.next !== pwd.value.again) { showFailToast('两次新密码不一致'); return false }
  try {
    await api('/api/auth/password', { method: 'PUT', json: { oldPassword: pwd.value.old, newPassword: pwd.value.next } })
    showSuccessToast('密码已修改')
    pwd.value = { old: '', next: '', again: '' }
    return true
  } catch (e: any) {
    showFailToast(e?.message || '修改失败')
    return false
  }
}
const version = __APP_VERSION__

const logoutOpen = ref(false)

/** 档案完善度提示（接口失败不阻塞页面） */
const profileHint = ref('')
onMounted(async () => {
  try {
    const d = await api<any>('/api/profile/me')
    profileHint.value = d.hasProfile ? (d.employeeNo || d.subjectName || d.title || '已完善') : ''
  } catch { /* 忽略 */ }
})

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.hero { padding-bottom: 56px; }
.me { display: flex; align-items: center; gap: 14px; }
.ava { display: flex; align-items: center; justify-content: center; width: 58px; height: 58px;
  border-radius: 50%; background: rgba(255,255,255,.92); color: var(--app-blue-deep);
  font-size: 22px; font-weight: 700; }
.me h1 { margin: 0 0 6px; font-size: 20px; font-weight: 800; }
.role-chip { background: rgba(255,255,255,.2); color: #fff; }

.cells { margin-top: 12px; padding: 4px 0; }
.cells.overlap { margin-top: -36px; }
.cells :deep(.van-cell) { padding: 13px 16px; font-size: 15px; }
.cells :deep(.van-cell .van-icon:not(.van-cell__right-icon)) { color: var(--app-blue); font-size: 17px; margin-right: 2px; }
.cells :deep(.logout) { color: #EF4444; }
.cells :deep(.logout .van-cell__title) { color: #EF4444; }

.srv-tip { padding: 12px 16px 0; font-size: 12px; color: var(--app-text-3); line-height: 1.5; }
.about { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 0 24px 24px; }
.about-pano { width: 100%; height: 92px; object-fit: cover; border-radius: 12px; margin-bottom: 8px; }
.about-badge { width: 46px; }
.about b { font-size: 15px; color: var(--app-text-1); }
.about p { margin: 0; font-size: 12px; color: var(--app-text-2); }
.about .motto { color: var(--app-gold); letter-spacing: 1px; }
.about .ver { margin-top: 4px; color: var(--app-text-3); }
</style>
