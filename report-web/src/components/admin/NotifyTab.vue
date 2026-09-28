<template>
  <div class="notify-tab">
    <el-alert type="info" :closable="false" show-icon
      title="不打开 App 也知道：新审批/新请假/教师注册/探活告警/体检周报 推到企业微信群机器人"
      description="企业微信群 → 群设置 → 添加群机器人 → 复制 Webhook 地址填到下方。系统级厂商推送（灵动岛/状态栏）待校方注册极光/个推拿 AppKey 后另行接入。" />

    <el-form label-width="130px" class="form" v-loading="loading">
      <div class="sec">企业微信群机器人</div>
      <el-form-item label="Webhook 地址">
        <el-input v-model="cfg.wecomWebhookUrl" placeholder="https://qyapi.weixin.qq.com/webhook/send?key=…" clearable />
      </el-form-item>
      <el-form-item label="启用群推送">
        <el-switch v-model="cfg.wecomEnabled" />
        <span class="hint">{{ cfg.wecomActive ? '（当前生效中）' : '' }}</span>
      </el-form-item>
      <el-form-item label="审批类推群">
        <el-switch v-model="cfg.wecomPushApprovals" />
        <span class="hint">新审批/新请假/教师注册（告警与周报不受此开关控制）</span>
      </el-form-item>

      <div class="sec">服务器探活</div>
      <el-form-item label="启用巡检告警">
        <el-switch v-model="cfg.alertEnabled" />
        <span class="hint">数据库 / MinIO / 磁盘余量，异常与恢复各推一次</span>
      </el-form-item>
      <el-form-item label="巡检间隔（分钟）">
        <el-input-number v-model="cfg.alertIntervalMin" :min="1" :max="1440" />
      </el-form-item>

      <div class="sec">数据体检周报</div>
      <el-form-item label="启用周报">
        <el-switch v-model="cfg.weeklyReportEnabled" />
      </el-form-item>
      <el-form-item label="周报日">
        <el-select v-model="cfg.weeklyReportDay" style="width: 140px">
          <el-option v-for="(d, i) in ['周一','周二','周三','周四','周五','周六','周日']" :key="i" :label="d" :value="i + 1" />
        </el-select>
        <span class="hint">每周当天 07:40 推送</span>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
        <el-button :disabled="!cfg.wecomWebhookUrl" @click="testWecom">发送测试</el-button>
        <el-button @click="runProbe">立即巡检</el-button>
        <el-button @click="runWeekly">立即出周报</el-button>
      </el-form-item>
    </el-form>

    <el-input v-if="result" type="textarea" :rows="6" :model-value="result" readonly class="result" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/http'

const loading = ref(false)
const saving = ref(false)
const result = ref('')
const cfg = ref<any>({})

async function load() {
  loading.value = true
  try {
    cfg.value = await api<any>('/api/admin/notify/config')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await api('/api/admin/notify/config', { method: 'PUT', json: cfg.value })
    ElMessage.success('已保存')
    await load()
  } finally {
    saving.value = false
  }
}

async function testWecom() {
  const r = await api<any>('/api/admin/notify/wecom-test', { method: 'POST', json: { url: cfg.value.wecomWebhookUrl } })
  ElMessage(r.result === 'ok' ? { type: 'success', message: '测试消息已送达群机器人' } : { type: 'error', message: '发送失败：' + r.result })
}

async function runProbe() {
  const checks = await api<any[]>('/api/admin/notify/probe-run', { method: 'POST' })
  result.value = checks.map((c) => `${c.ok ? '✅' : '❌'} ${c.label}：${c.detail}`).join('\n')
}

async function runWeekly() {
  const r = await api<any>('/api/admin/notify/weekly-run', { method: 'POST' })
  result.value = r.markdown
  ElMessage.success('周报已生成并推送')
}

onMounted(load)
</script>

<style scoped>
.notify-tab { max-width: 720px; }
.sec { margin: 14px 0 10px; padding-left: 8px; border-left: 3px solid var(--el-color-primary); font-weight: 600; }
.hint { margin-left: 10px; font-size: 12px; color: var(--el-text-color-secondary); }
.result { margin-top: 12px; font-family: monospace; }
</style>
