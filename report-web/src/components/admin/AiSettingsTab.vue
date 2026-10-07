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

    <h4 class="usage-title">图像生成（成长画像）</h4>
    <el-alert :type="cfg.imgEnabled ? 'success' : 'warning'" :closable="false" show-icon
      :title="cfg.imgEnabled ? `图像 AI 已配置生效（报告「我的成长画像」自动生成装饰图，模型：${cfg.imgModel}）` : '图像 AI 未配置（成长画像保持虚线占位框，不影响报告生成）'"
      description="第二把独立 key，只用于文生图；同等级+同最强素养格全校共用一张图，提示词不含学生个人信息。清空地址或模型名并保存 = 停用" />
    <el-form label-width="110px" class="form">
      <el-form-item label="服务商地址">
        <el-input v-model="imgForm.baseUrl" placeholder="例如 https://api.fluxapi.cloud/v1（OpenAI Images 兼容）" clearable />
      </el-form-item>
      <el-form-item label="API Key">
        <el-input v-model="imgForm.apiKey" type="password" show-password
          :placeholder="cfg.imgApiKeySet ? `已保存（${cfg.imgApiKeyMasked}），留空 = 不修改` : 'sk-…'" />
      </el-form-item>
      <el-form-item label="模型名">
        <el-input v-model="imgForm.model" placeholder="例如 grok-imagine-image" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="imgSaving" @click="saveImg">保存</el-button>
        <el-button :loading="imgTesting" @click="testImg">测试生图</el-button>
        <span class="hint">真实生成一张，约 10~30 秒</span>
      </el-form-item>
    </el-form>
    <div v-if="imgPreview" class="img-preview">
      <img :src="imgPreview" alt="测试生成结果" />
    </div>

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
// 批36① 图像生成：独立第二把 key，与文本 AI 分开保存/测试
const imgSaving = ref(false)
const imgTesting = ref(false)
const imgForm = reactive({ baseUrl: '', apiKey: '', model: '' })
const imgPreview = ref('')

async function load() {
  loading.value = true
  try {
    cfg.value = await api<any>('/api/admin/ai/config')
    form.baseUrl = cfg.value.baseUrl
    form.model = cfg.value.model
    form.apiKey = ''
    imgForm.baseUrl = cfg.value.imgBaseUrl
    imgForm.model = cfg.value.imgModel
    imgForm.apiKey = ''
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

/** 图像区保存：只发 img 三字段（文本字段为 null，后端不动） */
async function saveImg() {
  imgSaving.value = true
  try {
    cfg.value = await api<any>('/api/admin/ai/config', { method: 'PUT', json: { ...imgForm } })
    imgForm.baseUrl = cfg.value.imgBaseUrl
    imgForm.model = cfg.value.imgModel
    imgForm.apiKey = ''
    ElMessage.success('已保存，即时生效')
  } finally {
    imgSaving.value = false
  }
}

/** 真实生成一张样例图（固定=报告实际模板）回页预览；字段留空用已保存配置 */
async function testImg() {
  imgTesting.value = true
  imgPreview.value = ''
  try {
    const r = await api<any>('/api/admin/ai/test-image', { method: 'POST', json: { ...imgForm } })
    imgPreview.value = r.image
  } catch (e: any) {
    ElMessage.error(e?.message || String(e))
  } finally {
    imgTesting.value = false
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
.img-preview { margin-top: 8px; }
.img-preview img { max-width: 260px; border: 1px solid var(--el-border-color); border-radius: 6px; }
</style>
