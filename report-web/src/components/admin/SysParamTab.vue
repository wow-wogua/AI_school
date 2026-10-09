<template>
  <div class="sys-param">
    <el-alert type="info" :closable="false" show-icon
      title="常用系统参数（保存后即时生效，无需重启）"
      description="当前开放：学生请假分级审批的天数阈值、文明班评比口径切换日。" />

    <el-form label-width="150px" class="form" v-loading="loading">
      <div class="sec">学生请假分级审批</div>
      <el-form-item label="登记即生效上限（天）">
        <el-input-number v-model="p.leaveLevel1Days" :min="1" :max="364" />
        <span class="hint">不超过该天数：班主任 / 生活老师录入后直接生效</span>
      </el-form-item>
      <el-form-item label="级长审批上限（天）">
        <el-input-number v-model="p.leaveLevel2Days" :min="2" :max="365" />
        <span class="hint">超过上一档且不超过该天数：级长一级审批</span>
      </el-form-item>
      <el-form-item label="系统受理上限（天）">
        <el-input-number v-model="p.leaveMaxDays" :min="2" :max="365" />
        <span class="hint">超过级长上限且不超过该天数：级长 → 学成中心主任 → 书记 三级审批（批39）；再长请走纸质申请</span>
      </el-form-item>

      <div class="sec">文明班评比</div>
      <el-form-item label="口径切换日">
        <el-date-picker v-model="p.civilityEvalFrom" type="date" value-format="YYYY-MM-DD"
          placeholder="留空 = 仍按旧打分口径" clearable style="width: 180px" />
        <span class="hint">自该日起：文明班分 = 每日基础 120 + 素养评价分直加；此前的历史打分仍计入排名</span>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        <span class="hint">须满足 1 ≤ 即生效天数 &lt; 级长审批天数 ≤ 上限天数 ≤ 365</span>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/http'

const loading = ref(false)
const saving = ref(false)
const p = ref<any>({})

async function load() {
  loading.value = true
  try {
    p.value = await api<any>('/api/admin/sys-param')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    p.value = await api<any>('/api/admin/sys-param', {
      method: 'PUT',
      json: {
        leaveLevel1Days: p.value.leaveLevel1Days,
        leaveLevel2Days: p.value.leaveLevel2Days,
        leaveMaxDays: p.value.leaveMaxDays,
        civilityEvalFrom: p.value.civilityEvalFrom || '',
      },
    })
    ElMessage.success('已保存，即时生效')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.sys-param { max-width: 720px; }
.sec { margin: 14px 0 10px; padding-left: 8px; border-left: 3px solid var(--el-color-primary); font-weight: 600; }
.hint { margin-left: 10px; font-size: 12px; color: var(--el-text-color-secondary); }
.form { margin-top: 4px; }
</style>
