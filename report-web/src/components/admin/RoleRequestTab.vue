<template>
  <div>
    <el-alert v-if="!rows.length" title="暂无待审批申请" type="info" :closable="false"
      description="新建管理员/领导账号、或将教师升级为管理员/领导时，需另一名管理员/领导在此（或领导 App 端「待我审批」）审批通过后生效。" />
    <el-table v-else :data="rows" size="small">
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="row.reqType === 'CREATE' ? 'primary' : 'warning'" size="small">
            {{ row.reqType === 'CREATE' ? '新建账号' : '角色升级' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="username" label="登录名" width="120" />
      <el-table-column prop="realName" label="姓名" width="110" />
      <el-table-column label="目标" width="150">
        <template #default="{ row }">
          {{ row.reqType === 'CREATE'
            ? '新账号 · ' + row.targetRoleName
            : (row.fromRoleName ?? '') + ' → ' + row.targetRoleName }}
        </template>
      </el-table-column>
      <el-table-column prop="requesterName" label="发起人" width="110" />
      <el-table-column prop="createTime" label="发起时间" width="170">
        <template #default="{ row }">{{ fmt(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="160">
        <template #default="{ row }">
          <el-button type="success" size="small" @click="approve(row)">通过</el-button>
          <el-button type="danger" size="small" @click="reject(row)">拒绝</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../api/http'

/** 处理完一条后通知父级（AdminView 徽标即时刷新） */
const emit = defineEmits<{ handled: [] }>()

const rows = ref<any[]>([])
let timer: number | undefined

async function load() {
  rows.value = await api<any[]>('/api/role-request/list')
}

function fmt(t?: string) {
  return t ? t.slice(0, 16).replace('T', ' ') : ''
}

/** 通过口径（批2-5 拍板）：新建=账号启用；升级=角色生效 */
async function approve(row: any) {
  await ElMessageBox.confirm(
    `通过「${row.realName}」的${row.reqType === 'CREATE' ? '新账号' : '升级为' + row.targetRoleName}申请？`, '确认审批')
  await api(`/api/role-request/${row.id}/approve`, { method: 'PUT' })
  ElMessage.success('已通过')
  await load()
  emit('handled')
}

/** 拒绝口径（批2-5 拍板）：新建=账号删除；升级=角色不动（自动还原） */
async function reject(row: any) {
  const { value } = await ElMessageBox.prompt(
    `拒绝「${row.realName}」的申请？${row.reqType === 'CREATE' ? '该账号将被删除。' : '该教师角色保持不变。'}可填备注：`,
    '拒绝', { inputPlaceholder: '备注（可选）', inputValue: '' }).catch(() => ({ value: undefined as any }))
  if (value === undefined) return
  await api(`/api/role-request/${row.id}/reject`, { method: 'PUT', json: { note: value || null } })
  ElMessage.success('已拒绝')
  await load()
  emit('handled')
}

onMounted(() => {
  load()
  timer = window.setInterval(load, 30_000) // 审批人停留页面期间自动刷新
})
onUnmounted(() => window.clearInterval(timer))
</script>
