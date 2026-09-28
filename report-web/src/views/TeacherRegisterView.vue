<template>
  <div class="login-wrap">
    <div class="login-bg" aria-hidden="true"></div>
    <motion.div class="login-card reg-card"
      :initial="{ opacity: 0, y: 30, scale: 0.96 }" :animate="{ opacity: 1, y: 0, scale: 1 }"
      :transition="{ type: 'spring', stiffness: 240, damping: 22 }">
      <div class="login-head">
        <img class="login-logo" src="/badge.png" alt="">
        <h1>教师注册</h1>
        <p>提交后由管理员/领导审批，通过即可登录</p>
      </div>

      <div v-if="done" class="done">
        <van-icon name="passed" />
        <b>已提交审批</b>
        <p>请等待学校管理员或领导在「账号审批」中确认；<br>审批通过后即可用工号登录。</p>
        <van-button round block type="primary" class="login-btn" @click="$router.push('/login')">返回登录</van-button>
      </div>

      <van-form v-else @submit="doRegister">
        <van-cell-group inset :border="false" class="fields">
          <van-field v-model="form.username" left-icon="user-o" placeholder="工号（即登录账号）"
            :rules="[
              { required: true, message: '请输入工号' },
              { pattern: /^[A-Za-z0-9@._-]{3,64}$/, message: '工号须为 3-64 位字母/数字/符号(@._-)' }]" />
          <van-field v-model="form.realName" left-icon="contact" placeholder="真实姓名"
            :rules="[{ required: true, message: '请填写真实姓名' }]" />
          <van-field v-model="form.phone" type="tel" maxlength="11" left-icon="phone-o" placeholder="手机号（选填，方便联系）"
            :rules="[{ pattern: /^1\d{10}$/, message: '手机号格式不正确' }]" />
          <van-field v-model="form.password" type="password" left-icon="lock" placeholder="设置密码（至少 8 位）"
            :rules="[
              { required: true, message: '请设置密码' },
              { validator: (v: string) => v.length >= 8, message: '密码至少 8 位' }]" />
          <van-field v-model="form.password2" type="password" left-icon="lock" placeholder="确认密码"
            :rules="[
              { required: true, message: '请再次输入密码' },
              { validator: (v: string) => v === form.password, message: '两次密码不一致' }]" />
        </van-cell-group>
        <van-button round block type="primary" native-type="submit" class="login-btn" :loading="loading">
          提交注册
        </van-button>
      </van-form>

      <button v-if="!done" class="forgot" type="button" @click="$router.push('/login')">已有账号？返回登录</button>
      <p v-if="!done" class="tip">面向新入职/换届时未建号的老师：在册教师账号由学校统一导入，无需注册；忘记密码请联系管理员重置。</p>
    </motion.div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { motion } from 'motion-v'
import { showSuccessToast } from 'vant'
import { api } from '../api/http'

const form = reactive({ username: '', realName: '', phone: '', password: '', password2: '' })
const loading = ref(false)
const done = ref(false)

async function doRegister() {
  loading.value = true
  try {
    await api('/api/auth/teacher/register', {
      method: 'POST',
      json: {
        username: form.username.trim(),
        realName: form.realName.trim(),
        phone: form.phone.trim(),
        password: form.password,
      },
    })
    done.value = true
    showSuccessToast('已提交，等待审批')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.reg-card { max-height: 86vh; overflow-y: auto; }
.tip { margin: 10px 6px 0; font-size: 11px; line-height: 1.7; color: var(--app-text-3, #9aa3b8); text-align: left; }
.done { text-align: center; padding: 18px 6px 6px; }
.done .van-icon { font-size: 52px; color: #0D9467; }
.done b { display: block; margin: 10px 0 6px; font-size: 18px; }
.done p { margin: 0 0 16px; font-size: 13px; line-height: 1.8; color: var(--app-text-2, #5b6478); }
/* 与登录页同款视觉（深蓝渐变罩+卡片），样式与家长注册页共用构建方式 */
.login-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center;
  padding: 18px 14px; position: relative; overflow: hidden; }
.login-bg { position: absolute; inset: -40px; background: linear-gradient(200deg, #16264F 0%, #0E1833 100%); z-index: 0; }
.login-card { position: relative; z-index: 1; width: 100%; max-width: 400px;
  background: var(--app-card, #fff); border-radius: 20px;
  box-shadow: 0 16px 48px rgba(9,18,46,.45); padding: 28px 18px 18px; }
.login-head { text-align: center; margin-bottom: 18px; }
.login-logo { width: 52px; height: auto; }
.login-head h1 { margin: 10px 0 4px; font-size: 24px; font-weight: 800; letter-spacing: 4px;
  background: linear-gradient(150deg, #A8232B, #C9A227);
  -webkit-background-clip: text; background-clip: text; color: transparent; }
.login-head p { margin: 0; font-size: 12px; color: var(--app-text-2, #5b6478); letter-spacing: 1px; }
.fields { margin: 0 0 14px; border-radius: 14px; overflow: hidden; border: 1px solid var(--app-card-border, #e8ecf5); }
:deep(.fields .van-field) { padding: 12px 14px; font-size: 15px; }
.login-btn { height: 44px; font-size: 16px; font-weight: 600;
  background: linear-gradient(150deg, #8C1D23, #A8232B); border: none; }
.forgot { display: block; margin: 14px auto 0; background: none; border: none;
  font-size: 13px; color: var(--app-blue, #1B2E6B); }
</style>
