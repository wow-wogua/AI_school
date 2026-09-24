<template>
  <div class="app-page talk">
    <div class="app-sec">谈心记录</div>

    <!-- 发起卡 -->
    <div class="app-card tex-b form">
      <div class="picker-card" @click="showPicker = true">
        <span class="ava" :class="{ has: student }" :style="student ? { background: avaColor(student.name) } : {}">
          {{ student ? student.name.charAt(0) : '?' }}
        </span>
        <div class="p-info">
          <b>{{ student ? student.name : '点击选择学生' }}</b>
          <p>{{ student ? className : '选班后列出该班学生' }}</p>
        </div>
        <van-icon name="arrow" class="p-arrow" />
      </div>
      <van-field :model-value="talkDate" is-link readonly label="谈心日期" placeholder="必选"
        @click="openDate" />
      <van-field :model-value="talkType" is-link readonly label="类型" placeholder="选择类型（必选）"
        @click="typeOpen = true" />
      <van-field v-model="content" type="textarea" rows="3" autosize label="内容" maxlength="500" show-word-limit
        placeholder="谈心主题、学生状态与后续跟进（500 字内）" />
      <van-button round block type="primary" class="submit" :loading="submitting" @click="doSubmit">保存记录</van-button>
    </div>

    <div class="app-sec">我的谈心</div>
    <div class="app-card list">
      <div v-if="!rows.length" class="empty">还没有谈心记录</div>
      <div v-for="r in rows" :key="r.id" class="row">
        <div class="r-body">
          <p class="r-title">
            <van-tag plain type="primary" class="t-tag">{{ r.talkType }}</van-tag>
            {{ r.studentName }} <small class="r-cls">{{ r.className }}</small>
          </p>
          <p class="r-sub">{{ r.talkDate }} · 记录于 {{ fmtTime(r.createTime) }}</p>
          <p class="r-txt">{{ r.content }}</p>
        </div>
      </div>
    </div>

    <p class="app-foot">石实实验学校 · 石实SHINE</p>

    <!-- 班级/学生双列选择（同成长银行 cascade） -->
    <van-popup v-model:show="showPicker" position="bottom" round>
      <van-picker show-toolbar title="选择学生" :columns="pickerColumns" @cancel="showPicker = false"
        @confirm="onPick" />
    </van-popup>
    <van-popup v-model:show="typeOpen" position="bottom" round>
      <van-picker title="谈心类型" :columns="TALK_TYPES"
        @confirm="(ev: any) => { talkType = ev.selectedOptions?.[0]?.text || ''; typeOpen = false }"
        @cancel="typeOpen = false" />
    </van-popup>
    <van-popup v-model:show="dateOpen" position="bottom" round>
      <van-date-picker title="谈心日期" v-model="dateBuf" :columns-type="['year', 'month', 'day']"
        :min-date="minDate" :max-date="maxDate" @confirm="onDateOk" @cancel="dateOpen = false" />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { showSuccessToast, showToast } from 'vant'
import { api } from '../api/http'

interface Clazz { id: number; name: string }
interface Stu { id: number; name: string; classId?: number }

const TALK_TYPES = ['学业', '心理', '纪律', '生活', '其他'].map((t) => ({ text: t, value: t }))
const minDate = new Date(2020, 0, 1)
const maxDate = new Date(2030, 11, 31)

const classes = ref<Clazz[]>([])
const studentsByClass = ref<Record<number, Stu[]>>({})
const student = ref<Stu | null>(null)
const className = ref('')
const showPicker = ref(false)
const talkDate = ref('')
const dateBuf = ref<string[]>([])
const dateOpen = ref(false)
const talkType = ref('')
const typeOpen = ref(false)
const content = ref('')
const submitting = ref(false)
const rows = ref<any[]>([])

/** Vant4 cascade 树：班级 → 该班学生（value=id） */
const pickerColumns = computed(() => classes.value.map((c) => ({
  text: c.name,
  value: c.id,
  children: (studentsByClass.value[c.id] ?? []).map((s) => ({ text: s.name, value: s.id })),
})))

function onPick({ selectedOptions }: any) {
  const [cls, stu] = selectedOptions
  if (cls?.value && stu?.value) {
    const hit = studentsByClass.value[cls.value]?.find((s) => s.id === stu.value)
    if (hit) {
      student.value = { ...hit, classId: cls.value as number }
      className.value = cls.text
    }
  }
  showPicker.value = false
}

function openDate() {
  if (talkDate.value) {
    dateBuf.value = talkDate.value.split('-')
  } else { // 空 model 的 van-date-picker 会落 min-date（2020），须预置今天
    const now = new Date()
    dateBuf.value = [String(now.getFullYear()), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')]
  }
  dateOpen.value = true
}

function onDateOk() {
  talkDate.value = dateBuf.value.join('-')
  dateOpen.value = false
}

async function doSubmit() {
  if (!student.value) { showToast('请选择学生'); return }
  if (!talkDate.value) { showToast('请选择谈心日期'); return }
  if (!talkType.value) { showToast('请选择谈心类型'); return }
  if (!content.value.trim()) { showToast('请填写谈心内容'); return }
  submitting.value = true
  try {
    await api('/api/talk', {
      method: 'POST',
      json: { studentId: student.value.id, talkDate: talkDate.value, talkType: talkType.value, content: content.value.trim() },
    })
    showSuccessToast('已保存')
    content.value = ''
    await load()
  } catch (e: any) {
    showToast(e?.message || '保存失败')
  } finally { submitting.value = false }
}

async function load() {
  rows.value = await api<any[]>('/api/talk/my').catch(() => [])
}

function fmtTime(t: string) {
  return t.slice(0, 16).replace('T', ' ')
}

const palette = ['#A8232B', '#7C4DD8', '#0D9467', '#B07A1C', '#D6567A', '#3A7CA5']
function avaColor(name?: string) {
  if (!name) return palette[0]
  let h = 0
  for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) % 997
  return palette[h % palette.length]
}

onMounted(async () => {
  classes.value = await api<Clazz[]>('/api/meta/my-classes').catch(() => [])
  for (const c of classes.value) {
    studentsByClass.value[c.id] = await api<any>(`/api/student/list?classId=${c.id}&page=1&size=200`)
      .then((d: any) => d.records ?? d).catch(() => [])
  }
  await load()
})
</script>

<style scoped>
.form { padding: 14px 12px; margin-top: -36px; }
.picker-card { display: flex; align-items: center; gap: 12px; padding: 8px 4px; cursor: pointer; }
.ava { flex: none; width: 46px; height: 46px; border-radius: 50%; display: flex; align-items: center;
  justify-content: center; font-size: 18px; font-weight: 700; color: #fff; background: var(--app-text-3, #B7C0D2); }
.p-info { flex: 1; min-width: 0; }
.p-info b { font-size: 15px; color: var(--app-text-1); }
.p-info p { margin: 4px 0 0; font-size: 12px; color: var(--app-text-3); }
.submit { margin-top: 14px; }

.list { margin-top: 12px; padding: 6px 14px; }
.row { padding: 12px 0; }
.row + .row { border-top: 1px solid var(--app-card-border); }
.r-title { margin: 0; font-size: 14px; font-weight: 600; color: var(--app-text-1); }
.t-tag { margin-right: 6px; }
.r-cls { font-weight: 400; color: var(--app-text-3); margin-left: 4px; }
.r-sub { margin: 4px 0 0; font-size: 11px; color: var(--app-text-3); }
.r-txt { margin: 6px 0 0; font-size: 13px; color: var(--app-text-2); line-height: 1.55; white-space: pre-wrap; }
.empty { padding: 26px 0; text-align: center; font-size: 13px; color: var(--app-text-3); }
</style>
