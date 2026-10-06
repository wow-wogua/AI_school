<template>
  <div class="ai-settings">
    <el-alert :type="cfg.enabled ? 'success' : 'warning'" :closable="false" show-icon
      :title="cfg.enabled ? 'AI 已配置生效（填报告寄语 / 荣誉证书识别会走大模型）' : 'AI 未配置（寄语与证书识别自动降级为模板草稿，不影响其他功能）'"
      :description="cfg.enabled ? `当前模型：${cfg.effectiveModel}` : '填写 OpenAI 兼容服务商的地址 / API Key / 模型名，保存后即时生效，无需重启'" />

    <el-form label-width="110px" class="form" v-loading="loading">
      <el-form-item label="服务商地址">
        <el-input v-model="form.baseUrl" placeholder="例如 https://api.deepseek.com/v1（OpenAI 兼容）" clearable />
      </el-form-item>
      <el-form-item label="API Key">
        <el-input v-model="form.apiKey" type="password" show-password
          :placeholder="cfg.apiKeySet ? `已保存（${cfg.apiKeyMasked}），留空 = 不修改` : 'sk-…'" />
      </el-form-item>
      <el-form-item label="模型名">
        <el-input v-model="form.model" placeholder="例如 deepseek-chat" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        <el-button :loading="testing" @click="test">测试连接</el-button>
        <span class="hint">清空地址或模型名并保存 = 停用 AI</span>
      </el-form-item>
    </el-form>

    <el-alert v-if="testResult" :type="testOk ? 'success' : 'error'" :closable="false" show-icon
      :title="testOk ? '连接成功，模型回复：' + testResult : testResult" class="test-result" />

    <h4 class="usage-title">用量统计</h4>
    <AiUsageTab />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/http'
import AiUsageTab from './AiUsageTab.vue'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const cfg = ref<any>({})
const form = reactive({ baseUrl: '', apiKey: '', model: '' })
const testResult = ref('')
const testOk = ref(false)

async function load() {
  loading.value = true
  try {
    cfg.value = await api<any>('/api/admin/ai/config')
    form.baseUrl = cfg.value.baseUrl
    form.model = cfg.value.model
    form.apiKey = ''
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    cfg.value = await api<any>('/api/admin/ai/config', { method: 'PUT', json: { ...form } })
    form.baseUrl = cfg.value.baseUrl
    form.model = cfg.value.model
    form.apiKey = ''
    ElMessage.success('已保存，即时生效')
  } finally {
    saving.value = false
  }
}

/** 字段留空 = 用已保存配置测（后端兜底） */
async function test() {
  testing.value = true
  testResult.value = ''
  try {
    const r = await api<any>('/api/admin/ai/test', { method: 'POST', json: { ...form } })
    testResult.value = r.result
    testOk.value = true
  } catch (e: any) {
    testResult.value = e?.message || String(e)
    testOk.value = false
  } finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.ai-settings { max-width: 720px; }
.hint { margin-left: 10px; font-size: 12px; color: var(--el-text-color-secondary); }
.form { margin-top: 14px; }
.test-result { margin-top: 4px; }
.usage-title { margin: 22px 0 4px; padding-left: 8px; border-left: 3px solid var(--el-color-primary); font-size: 14px; font-weight: 600; }
</style>
