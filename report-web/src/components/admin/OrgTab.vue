<template>
  <div>
    <h4>年级</h4>
    <div class="toolbar">
      <el-button type="primary" size="small" @click="openGrade()">新建年级</el-button>
    </div>
    <el-table :data="grades" size="small">
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column prop="schoolYear" label="学年" width="130" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button link type="primary" @click="openGrade(row)">编辑</el-button>
          <el-button link type="danger" @click="removeGrade(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <h4>班级</h4>
    <div class="toolbar">
      <el-button type="primary" size="small" @click="openClass()">新建班级</el-button>
    </div>
    <el-table :data="classList" size="small">
      <el-table-column prop="gradeName" label="年级" width="140" />
      <el-table-column prop="name" label="班级" width="140" />
      <el-table-column prop="headTeacherName" label="班主任" width="110" />
      <el-table-column label="操作" width="250">
        <template #default="{ row }">
          <el-button link type="primary" @click="openClass(row)">编辑</el-button>
          <el-button link type="danger" @click="removeClass(row)">删除</el-button>
          <el-button link type="primary" @click="openMove(row)">整班调班</el-button>
          <el-button link type="warning" @click="markGraduate(row)">标记毕业</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="gradeDialog" :title="gradeForm.id ? '编辑年级' : '新建年级'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="gradeForm.name" /></el-form-item>
        <el-form-item label="学年"><el-input v-model="gradeForm.schoolYear" placeholder="如 2025-2026" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="gradeDialog = false">取消</el-button>
        <el-button type="primary" @click="saveGrade">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="classDialog" :title="classForm.id ? '编辑班级' : '新建班级'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="年级">
          <el-select v-model="classForm.gradeId" style="width: 100%">
            <el-option v-for="g in grades" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="classForm.name" placeholder="如 初一(3)班" /></el-form-item>
        <el-form-item label="班主任">
          <el-select v-model="classForm.headTeacherId" clearable filterable style="width: 100%">
            <el-option v-for="t in teachers" :key="t.id" :label="t.realName" :value="t.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classDialog = false">取消</el-button>
        <el-button type="primary" @click="saveClass">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="moveDialog" :title="`整班调班：${moveForm.fromName}`" width="440px">
      <div style="margin-bottom: 10px; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6">
        全班「在读」学生移入目标班级（升年级：先建好新年级的班级，再把各班整班调入）。转出/毕业学生不动，历史成长数据保留。
      </div>
      <el-form label-width="80px">
        <el-form-item label="目标班级">
          <el-select v-model="moveForm.targetClassId" placeholder="选择目标班级" style="width: 100%">
            <el-option v-for="c in classList.filter((x: any) => x.id !== moveForm.fromId)" :key="c.id"
              :label="`${c.gradeName} ${c.name}`" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="moveDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!moveForm.targetClassId" @click="doMove">调班</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../api/http'

const grades = ref<any[]>([])
const classList = ref<any[]>([])
const teachers = ref<any[]>([])
const gradeDialog = ref(false)
const classDialog = ref(false)
const gradeForm = ref<any>({})
const classForm = ref<any>({})
const moveDialog = ref(false)
const moveForm = ref<{ fromId: number; fromName: string; targetClassId?: number }>({ fromId: 0, fromName: '' })

async function load() {
  grades.value = await api<any[]>('/api/admin/grade')
  classList.value = await api<any[]>('/api/admin/class/list')
  const d = await api<{ records: any[] }>('/api/admin/user/list?page=1&size=100')
  teachers.value = d.records.filter((u: any) => u.role === 'HEAD_TEACHER' || u.role === 'TEACHER')
}

function openGrade(row?: any) {
  gradeForm.value = row ? { ...row } : {}
  gradeDialog.value = true
}

async function saveGrade() {
  if (gradeForm.value.id) {
    await api(`/api/admin/grade/${gradeForm.value.id}`, { method: 'PUT', json: gradeForm.value })
  } else {
    await api('/api/admin/grade', { method: 'POST', json: gradeForm.value })
  }
  ElMessage.success('已保存')
  gradeDialog.value = false
  await load()
}

async function removeGrade(row: any) {
  await ElMessageBox.confirm(`删除年级 ${row.name}？`, '确认')
  await api(`/api/admin/grade/${row.id}`, { method: 'DELETE' })
  ElMessage.success('已删除')
  await load()
}

function openClass(row?: any) {
  classForm.value = row ? { ...row } : {}
  classDialog.value = true
}

async function saveClass() {
  if (classForm.value.id) {
    await api(`/api/admin/class/${classForm.value.id}`, { method: 'PUT', json: classForm.value })
  } else {
    await api('/api/admin/class', { method: 'POST', json: classForm.value })
  }
  ElMessage.success('已保存')
  classDialog.value = false
  await load()
}

async function removeClass(row: any) {
  await ElMessageBox.confirm(`删除班级 ${row.name}？`, '确认')
  await api(`/api/admin/class/${row.id}`, { method: 'DELETE' })
  ElMessage.success('已删除')
  await load()
}

/** 整班调班（升年级/分班）：全班在读学生一键移入目标班级 */
function openMove(row: any) {
  moveForm.value = { fromId: row.id, fromName: `${row.gradeName} ${row.name}` }
  moveDialog.value = true
}

async function doMove() {
  const r = await api<{ moved: number }>(`/api/admin/class/${moveForm.value.fromId}/move-students`, {
    method: 'PUT',
    json: { targetClassId: moveForm.value.targetClassId },
  })
  ElMessage.success(`已把 ${r.moved} 名在读学生调入目标班级`)
  moveDialog.value = false
  await load()
}

/** 全班标记毕业：换届时整届退场，档案与报告保留可查 */
async function markGraduate(row: any) {
  await ElMessageBox.confirm(`将 ${row.gradeName} ${row.name} 全班在读学生标记为「毕业」？档案与已生成报告保留可查`, '确认')
  const r = await api<{ updated: number }>(`/api/admin/class/${row.id}/mark-status`, {
    method: 'PUT',
    json: { status: '毕业' },
  })
  ElMessage.success(`已标记 ${r.updated} 名学生毕业`)
}

onMounted(load)
</script>
