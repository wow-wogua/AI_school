<template>
  <div class="app-page civility">
    <div class="app-sec">文明班打分</div>

    <!-- 发起卡：班级 → 日期 → 大项 → 条目 → 分值/人次/备注 -->
    <div class="app-card tex-b form">
      <div class="picker-card" @click="classOpen = true">
        <span class="ava" :class="{ has: cls }">{{ cls ? cls.name.charAt(0) : '班' }}</span>
        <div class="p-info">
          <b>{{ cls ? cls.name : '点击选择班级' }}</b>
          <p>{{ cls ? cls.gradeName : '值日巡查可给全校任意班级打分' }}</p>
        </div>
        <van-icon name="arrow" class="p-arrow" />
      </div>
      <van-field :model-value="scoreDate" is-link readonly label="日期" placeholder="必选" @click="openDate" />
      <van-field :model-value="sectionName" is-link readonly label="大项（1-12）" placeholder="选择考核大项"
        @click="secOpen = true" />
      <van-field :model-value="itemText" is-link readonly label="具体条目" placeholder="先选大项再选条目"
        :disabled="!sectionName" @click="itemOpen = true" />
      <div class="num-row">
        <div class="num-cell">
          <span class="n-label">分值/次</span>
          <van-stepper v-model="delta" :step="0.5" :min="-50" :max="50" allow-empty />
        </div>
        <div class="num-cell">
          <span class="n-label">人次</span>
          <van-stepper v-model="cnt" :min="1" :max="99" integer />
        </div>
      </div>
      <p v-if="delta === null" class="hint">该条目分值区间（如 1-10 分）或未标明，请按实际手动填分值；负数=扣分</p>
      <van-field v-model="note" label="备注" maxlength="200" placeholder="选填，如人名/地点" />
      <van-button round block type="primary" class="submit" :loading="submitting" @click="doSubmit">提交打分</van-button>
    </div>

    <div class="app-sec">今日全校打分</div>
    <div class="app-card list">
      <div v-if="!records.length" class="empty">今日暂无打分记录</div>
      <div v-for="r in records" :key="r.id" class="row">
        <div class="r-body">
          <p class="r-title">
            <van-tag plain :type="Number(r.delta) >= 0 ? 'warning' : 'danger'">
              {{ Number(r.delta) >= 0 ? '+' : '' }}{{ r.delta }}×{{ r.cnt }}
            </van-tag>
            <b>{{ r.className }}</b> · {{ r.itemText }}
          </p>
          <p class="r-sub">{{ r.scoreDate }} · {{ r.operatorName }}<template v-if="r.note"> · {{ r.note }}</template></p>
        </div>
      </div>
    </div>

    <!-- 自动汇总：本月排名（年级分组，各卡前 5 名） -->
    <div class="app-sec">本月排名（自动汇总）</div>
    <div class="app-card tex-e basis">
      <p class="basis-txt">班级当日分 = 基础 120 + 当日打分合计；区间总分 = 检查日数×120 + 累计打分。细则见「德育规范」。</p>
    </div>
    <div v-for="g in rankGrades" :key="g.gradeId" class="app-card rank-card">
      <p class="g-head">{{ g.gradeName || '年级' }}<small>检查 {{ g.checkDays }} 天</small></p>
      <div v-for="c in g.classes.slice(0, 5)" :key="c.classId" class="rk" :class="'top-' + c.rankNo">
        <span class="no">{{ c.rankNo }}</span>
        <b>{{ c.className }}</b>
        <span class="sc">{{ c.totalScore }}</span>
      </div>
    </div>

    <p class="app-foot">石实实验学校 · 石实SHINE</p>

    <!-- 班级/大项/条目三级弹层 -->
    <van-popup v-model:show="classOpen" position="bottom" round>
      <van-picker show-toolbar title="选择班级" :columns="classColumns"
        @cancel="classOpen = false" @confirm="onClass" />
    </van-popup>
    <van-popup v-model:show="secOpen" position="bottom" round>
      <van-picker title="大项（1-12）" :columns="secColumns"
        @confirm="(ev: any) => { sectionName = ev.selectedOptions?.[0]?.text || ''; sectionNo = ev.selectedOptions?.[0]?.value; onSection(); secOpen = false }"
        @cancel="secOpen = false" />
    </van-popup>
    <van-popup v-model:show="itemOpen" position="bottom" round>
      <van-picker title="具体条目" :columns="itemColumns"
        @confirm="(ev: any) => { onItem(ev.selectedOptions?.[0]?.value); itemOpen = false }"
        @cancel="itemOpen = false" />
    </van-popup>
    <van-popup v-model:show="dateOpen" position="bottom" round>
      <van-date-picker title="打分日期" v-model="dateBuf" :columns-type="['year', 'month', 'day']"
        :min-date="minDate" :max-date="maxDate" @confirm="onDateOk" @cancel="dateOpen = false" />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { showSuccessToast, showToast } from 'vant'
import { api } from '../api/http'
import { CONDUCT_SECTIONS } from '../data/conductRules'

interface Cls { id: number; name: string; gradeId: number; gradeName: string }

const today = new Date()
const fmt = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

const classes = ref<Cls[]>([])
const cls = ref<Cls | null>(null)
const classOpen = ref(false)
const scoreDate = ref(fmt(today))
const dateBuf = ref<string[]>(scoreDate.value.split('-'))
const dateOpen = ref(false)
const minDate = new Date(2020, 0, 1)
const maxDate = new Date(2030, 11, 31)

const sectionName = ref('')
const sectionNo = ref<number>()
const secOpen = ref(false)
const itemText = ref('')
const itemOpen = ref(false)
const delta = ref<number | null>(null)
const cnt = ref(1)
const note = ref('')
const submitting = ref(false)
const records = ref<any[]>([])
const rankGrades = ref<any[]>([])

const classColumns = computed(() => classes.value.map((c) => ({ text: `${c.gradeName}${c.name}`, value: c.id })))
const secColumns = CONDUCT_SECTIONS.map((s, i) => ({ text: s.name, value: i + 1 }))
/** 选中大项下的全部条目拍平（跨 groups），value=文本 */
const itemColumns = computed(() => {
  const s = CONDUCT_SECTIONS[(sectionNo.value ?? 1) - 1]
  return (s?.groups ?? []).flatMap((g) => g.items).map((it) => ({ text: it.text, value: it.text }))
})

function onClass(ev: any) {
  const hit = classes.value.find((c) => c.id === ev.selectedOptions?.[0]?.value)
  cls.value = hit || null
  classOpen.value = false
}

function onSection() {
  itemText.value = ''
  delta.value = null
}

/** 选条目后：条目自带分值可解析则预填，区间/未标明则留空手填 */
function onItem(text: string) {
  itemText.value = text || ''
  const s = CONDUCT_SECTIONS[(sectionNo.value ?? 1) - 1]
  const it = (s?.groups ?? []).flatMap((g) => g.items).find((x) => x.text === itemText.value)
  const n = it?.delta ? Number(it.delta) : NaN
  delta.value = Number.isFinite(n) && n !== 0 ? n : null
}

function openDate() {
  dateBuf.value = scoreDate.value.split('-')
  dateOpen.value = true
}

function onDateOk() {
  scoreDate.value = dateBuf.value.join('-')
  dateOpen.value = false
}

async function doSubmit() {
  if (!cls.value) { showToast('请选择班级'); return }
  if (!sectionNo.value) { showToast('请选择大项'); return }
  if (!itemText.value) { showToast('请选择具体条目'); return }
  if (delta.value === null || delta.value === 0) { showToast('请填写分值（非零，负数=扣分）'); return }
  submitting.value = true
  try {
    await api('/api/civility/score', {
      method: 'POST',
      json: {
        classId: cls.value.id, scoreDate: scoreDate.value, sectionNo: sectionNo.value,
        itemText: itemText.value, delta: delta.value, cnt: cnt.value, note: note.value.trim() || undefined,
      },
    })
    showSuccessToast('已记录')
    itemText.value = ''
    delta.value = null
    cnt.value = 1
    note.value = ''
    await Promise.all([loadRecords(), loadRank()])
  } catch (e: any) {
    showToast(e?.message || '提交失败')
  } finally { submitting.value = false }
}

async function loadRecords() {
  records.value = await api<any[]>(`/api/civility/records?from=${fmt(today)}&to=${fmt(today)}`).catch(() => [])
}

async function loadRank() {
  const from = fmt(new Date(today.getFullYear(), today.getMonth(), 1))
  const d = await api<any>(`/api/civility/rank?from=${from}&to=${fmt(today)}`).catch(() => null)
  rankGrades.value = d?.grades ?? []
}

onMounted(async () => {
  classes.value = await api<any[]>('/api/civility/classes').catch(() => [])
  await Promise.all([loadRecords(), loadRank()])
})
</script>

<style scoped>
.form { padding: 14px 12px; margin-top: 12px; }
.picker-card { display: flex; align-items: center; gap: 12px; padding: 8px 4px; cursor: pointer; }
.ava { flex: none; width: 46px; height: 46px; border-radius: 50%; display: flex; align-items: center;
  justify-content: center; font-size: 17px; font-weight: 700; color: #fff; background: var(--app-text-3, #B7C0D2); }
.p-info { flex: 1; min-width: 0; }
.p-info b { font-size: 15px; color: var(--app-text-1); }
.p-info p { margin: 4px 0 0; font-size: 12px; color: var(--app-text-3); }
.num-row { display: flex; gap: 18px; padding: 10px 16px; }
.num-cell { flex: 1; display: flex; align-items: center; justify-content: space-between; }
.n-label { font-size: 14px; color: var(--app-text-1); }
.hint { margin: 0 16px 8px; font-size: 11px; color: var(--app-text-3); line-height: 1.5; }
.submit { margin-top: 14px; }

.list { margin-top: 12px; padding: 6px 14px; }
.empty { padding: 26px 0; text-align: center; font-size: 13px; color: var(--app-text-3); }
.row { padding: 12px 0; }
.row + .row { border-top: 1px solid var(--app-card-border); }
.r-title { margin: 0; font-size: 13px; color: var(--app-text-1); line-height: 1.6; }
.r-title b { font-size: 14px; }
.r-sub { margin: 4px 0 0; font-size: 11px; color: var(--app-text-3); }

.basis { padding: 12px 16px; margin-top: 12px; }
.basis-txt { margin: 0; font-size: 12px; line-height: 1.7; color: var(--app-text-2); }

.rank-card { padding: 12px 14px; margin-top: 12px; }
.g-head { margin: 0 0 6px; font-size: 14px; font-weight: 700; color: var(--app-text-1); }
.g-head small { margin-left: 8px; font-size: 11px; font-weight: 400; color: var(--app-text-3); }
.rk { display: flex; align-items: center; gap: 10px; padding: 7px 2px; font-size: 13px; }
.no { flex: none; width: 22px; height: 22px; border-radius: 50%; display: flex; align-items: center;
  justify-content: center; font-size: 12px; font-weight: 700; background: var(--app-card-border); color: var(--app-text-2); }
.rk.top-1 .no { background: #EAB308; color: #fff; }
.rk.top-2 .no { background: #94A3B8; color: #fff; }
.rk.top-3 .no { background: #D97706; color: #fff; }
.rk b { flex: 1; color: var(--app-text-1); }
.sc { font-weight: 700; color: var(--app-text-2); }
</style>
