<template>
  <div>
    <el-alert type="info" :closable="false" show-icon class="copy-alert"
      title="常用文案（生效模板，保存后下一次生成报告即生效）"
      description="校名 / 学校简介 / 九格评价介绍 / 成长格言 / 办学理念。改文案不动版式结构；下方草稿区供整体改版时使用。" />

    <el-form label-width="90px" class="copy-form" v-loading="copyLoading">
      <div class="row2">
        <el-form-item label="学校名"><el-input v-model="copy.schoolName" maxlength="64" /></el-form-item>
        <el-form-item label="成长格言"><el-input v-model="copy.motto" maxlength="60" /></el-form-item>
      </div>
      <el-form-item label="格言出处"><el-input v-model="copy.mottoSource" maxlength="60" /></el-form-item>
      <el-form-item label="格言解读"><el-input v-model="copy.mottoNote" type="textarea" :rows="2" maxlength="120" /></el-form-item>
      <el-form-item label="学校简介"><el-input v-model="copy.intro" type="textarea" :rows="3" maxlength="500" /></el-form-item>
      <el-form-item label="九格介绍"><el-input v-model="copy.nineGridIntro" type="textarea" :rows="3" maxlength="500" /></el-form-item>
      <el-form-item label="办学理念">
        <div class="philosophy">
          <div v-for="(p, i) in copy.philosophy" :key="i" class="ph-row">
            <el-input v-model="p[0]" class="ph-name" maxlength="12" placeholder="名称（如：德）" />
            <el-input v-model="p[1]" class="ph-text" maxlength="60" placeholder="一句话说明" />
            <el-button link type="danger" :disabled="copy.philosophy.length <= 1" @click="copy.philosophy.splice(i, 1)">删除</el-button>
          </div>
          <el-button v-if="copy.philosophy.length < 8" link type="primary" @click="copy.philosophy.push(['', ''])">+ 加一行</el-button>
        </div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="copySaving" @click="saveCopy">保存文案</el-button>
      </el-form-item>
    </el-form>

    <div class="toolbar">
      <span class="draft-title">草稿模板（整体改版用）</span>
      <el-button type="primary" @click="openCreate">新建草稿模板</el-button>
    </div>
    <el-table :data="templates" size="small">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="schoolName" label="学校名" width="200" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <template v-if="row.status === '启用'">
            <el-tooltip content="启用模板为契约基线，锁定只读">
              <el-button link disabled>锁定</el-button>
            </el-tooltip>
          </template>
          <template v-else>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑草稿' : '新建草稿'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="学校名"><el-input v-model="form.schoolName" /></el-form-item>
        <el-form-item label="板块 JSON">
          <el-input v-model="form.sections" type="textarea" :rows="10" placeholder='{"sections": {...}}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../api/http'

const templates = ref<any[]>([])
const dialog = ref(false)
const form = ref<any>({})

// 常用文案（批35）：生效模板的文案字段直改，白名单在后端
const copyLoading = ref(false)
const copySaving = ref(false)
const copy = ref<any>({ philosophy: [] })

async function loadCopy() {
  copyLoading.value = true
  try {
    copy.value = await api<any>('/api/admin/template/copy')
  } finally {
    copyLoading.value = false
  }
}

async function saveCopy() {
  copySaving.value = true
  try {
    await api('/api/admin/template/copy', { method: 'PUT', json: copy.value })
    ElMessage.success('已保存，下一次生成报告即生效')
  } finally {
    copySaving.value = false
  }
}

async function load() {
  templates.value = await api<any[]>('/api/admin/template/list')
}

function openCreate() {
  form.value = { schoolName: '', sections: '{}' }
  dialog.value = true
}

function openEdit(row: any) {
  form.value = { ...row }
  dialog.value = true
}

async function save() {
  try {
    JSON.parse(form.value.sections)
  } catch {
    ElMessage.error('板块必须是合法 JSON')
    return
  }
  if (form.value.id) {
    await api(`/api/admin/template/${form.value.id}`, { method: 'PUT', json: form.value })
  } else {
    await api('/api/admin/template', { method: 'POST', json: form.value })
  }
  ElMessage.success('已保存（草稿）')
  dialog.value = false
  await load()
}

async function remove(row: any) {
  await ElMessageBox.confirm(`删除草稿模板 ${row.schoolName}？`, '确认')
  await api(`/api/admin/template/${row.id}`, { method: 'DELETE' })
  ElMessage.success('已删除')
  await load()
}

onMounted(() => { load(); loadCopy() })
</script>

<style scoped>
.copy-alert { margin-bottom: 4px; }
.copy-form { max-width: 720px; margin-top: 10px; }
.row2 { display: flex; gap: 12px; }
.row2 .el-form-item { flex: 1; }
.philosophy { width: 100%; }
.ph-row { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }
.ph-name { width: 140px; flex: none; }
.ph-text { flex: 1; }
.draft-title { font-weight: 600; margin-right: auto; }
</style>
