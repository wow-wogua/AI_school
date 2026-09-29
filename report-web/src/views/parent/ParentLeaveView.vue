<template>
  <div class="app-page p-leave">
    <!-- 发起请假（替绑定孩子） -->
    <div class="app-card tex-b form">
      <div v-if="children.length > 1" class="kid-chips">
        <button v-for="c in children" :key="c.studentId" type="button" class="k-chip"
          :class="{ on: form.studentId === c.studentId }" @click="form.studentId = c.studentId">
          {{ c.name }}<small>{{ c.className }}</small>
        </button>
      </div>
      <div v-else-if="children.length === 1" class="only-kid">为 <b>{{ children[0].name }}</b>（{{ children[0].className || '未分班' }}）提交请假</div>
      <div v-else class="only-kid">尚未绑定孩子，请联系班主任开通</div>

      <div class="type-row">
        <button v-for="t in types" :key="t" type="button" class="t-chip" :class="{ on: form.leaveType === t }"
          @click="form.leaveType = t">{{ t }}</button>
      </div>

      <div class="date-row" @click="picker.open = 'start'">
        <span>开始日期</span><b>{{ form.startDate || '请选择' }}</b><van-icon name="arrow" />
      </div>
      <div class="date-row" @click="picker.open = 'end'">
        <span>结束日期</span><b>{{ form.endDate || '请选择' }}</b><van-icon name="arrow" />
      </div>

      <van-field v-model="form.reason" type="textarea" rows="2" autosize label="请假事由"
        placeholder="请填写请假事由（必填，300 字内）" :maxlength="300" class="f-in" />

      <div class="photo-row">
        <div class="ph-lbl">凭证照片（≤3 张，可选，如病假条）</div>
        <div class="thumbs">
          <div v-for="(p, i) in photos" :key="i" class="thumb">
            <img :src="p.url" alt="凭证" />
            <button type="button" class="rm" @click="photos.splice(i, 1)"><van-icon name="cross" /></button>
          </div>
          <button v-if="photos.length < 3" type="button" class="add" @click="fileInput?.click()">
            <van-icon name="photograph" /><span>拍照/选图</span>
          </button>
        </div>
        <input ref="fileInput" type="file" accept="image/jpeg,image/png" multiple class="hide" @change="onPick" />
      </div>

      <van-button round block type="primary" class="submit" :loading="submitting"
        :disabled="!children.length" @click="doSubmit">提交请假</van-button>
    </div>

    <!-- 我的请假 -->
    <div class="app-sec">请假记录</div>
    <div class="app-card list">
      <van-skeleton v-if="!loaded" :row="4" />
      <div v-else-if="!rows.length" class="empty">还没有请假记录</div>
      <div v-for="r in rows" :key="r.id" class="row" @click="openDetail(r)">
        <div class="r-body">
          <p class="r-title">{{ r.studentName }} · {{ r.leaveType }} · {{ r.startDate }}<template v-if="r.endDate !== r.startDate"> ~ {{ r.endDate }}</template></p>
          <p class="r-sub">{{ relTime(r.createTime) }} 提交<template v-if="r.status !== 'PENDING' && r.approveNote"> · {{ r.approveNote }}</template></p>
        </div>
        <div class="r-side">
          <span class="st" :class="stClass(r.status)">{{ stLabel(r.status) }}</span>
          <button v-if="r.status === 'PENDING'" class="cancel" type="button" @click.stop="askCancel(r)">撤回</button>
        </div>
      </div>
    </div>

    <!-- 详情弹层 -->
    <van-popup v-model:show="detailOpen" position="bottom" round :style="{ maxHeight: '82%' }" class="pop">
      <template v-if="detail">
        <div class="p-head">
          <b>{{ detail.studentName }} · {{ detail.leaveType }}</b>
          <small>{{ detail.startDate }} ~ {{ detail.endDate }}</small>
        </div>
        <div class="p-body">
          <div class="app-sec" style="margin: 0 0 4px">请假事由</div>
          <p class="desc">{{ detail.reason }}</p>
          <template v-if="detailUrls.length">
            <div class="app-sec" style="margin: 14px 0 4px">凭证照片（{{ detailUrls.length }}）</div>
            <div class="big-photos"><img v-for="(u, i) in detailUrls" :key="i" :src="u" alt="凭证" /></div>
          </template>
          <div class="app-sec" style="margin: 14px 0 4px">审批与出入</div>
          <div class="detail-cells">
            <p><span>状态</span><b>{{ stLabel(detail.status) }}</b></p>
            <p v-if="detail.approverName"><span>审批老师</span><b>{{ detail.approverName }}</b></p>
            <p v-if="detail.approveTime"><span>审批时间</span><b>{{ fmtTime(detail.approveTime) }}</b></p>
            <p v-if="detail.approveNote"><span>审批意见</span><b>{{ detail.approveNote }}</b></p>
            <p v-if="detail.leaveTime"><span>离校登记</span><b>{{ fmtTime(detail.leaveTime) }}</b></p>
            <p v-if="detail.returnTime"><span>返校登记</span><b>{{ fmtTime(detail.returnTime) }}</b></p>
          </div>
        </div>
      </template>
    </van-popup>

    <!-- 日期选择（空值预置今天，批11 教训） -->
    <van-popup v-model:show="pickerShow" position="bottom" round>
      <van-date-picker title="选择日期" v-model="dateBuf" :columns-type="['year', 'month', 'day']"
        :min-date="minDate" :max-date="maxDate" @confirm="onDate" @cancel="pickerShow = false" />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import { api, apiForm, fetchBlob } from '../../api/http'
import { relTime } from '../../utils/fmt'

interface Kid { studentId: number; name: string; className?: string }
interface LeaveRow {
  id: number; studentId: number; studentName: string; studentNo?: string; className?: string
  leaveType: string; startDate: string; endDate: string; reason: string; status: string
  approverName: string; approveNote: string; approveTime?: string
  leaveTime?: string; returnTime?: string; createTime: string
  photoCount: number; photoUrls: string[]
}

const types = ['病假', '事假', '其他']
const children = ref<Kid[]>([])
const form = reactive({ studentId: 0, leaveType: '病假', startDate: '', endDate: '', reason: '' })
const photos = ref<{ file: File; url: string }[]>([])
const fileInput = ref<HTMLInputElement>()
const submitting = ref(false)
const rows = ref<LeaveRow[]>([])
const loaded = ref(false)
const detail = ref<LeaveRow | null>(null)
const detailOpen = ref(false)
const detailUrls = ref<string[]>([])

/* 日期弹层：picker.open 标记当前编辑的是 start 还是 end */
const picker = reactive({ open: '' as 'start' | 'end' | '' })
const pickerShow = computed({
  get: () => !!picker.open,
  set: (v: boolean) => { if (!v) picker.open = '' },
})
const today = new Date()
const minDate = new Date(today.getFullYear() - 1, 0, 1)
const maxDate = new Date(today.getFullYear() + 1, 11, 31)
const dateBuf = ref<string[]>([])

watch(() => picker.open, (v) => {
  if (!v) return
  const cur = v === 'start' ? form.startDate : form.endDate
  dateBuf.value = cur
    ? cur.split('-')
    : [String(today.getFullYear()), String(today.getMonth() + 1).padStart(2, '0'), String(today.getDate()).padStart(2, '0')]
})

function onDate({ selectedValues }: { selectedValues: string[] }) {
  if (picker.open === 'start') form.startDate = selectedValues.join('-')
  else form.endDate = selectedValues.join('-')
  picker.open = ''
}

function onPick(e: Event) {
  const files = Array.from((e.target as HTMLInputElement).files ?? [])
  for (const f of files) {
    if (photos.value.length >= 3) break
    photos.value.push({ file: f, url: URL.createObjectURL(f) })
  }
  ;(e.target as HTMLInputElement).value = ''
}

async function doSubmit() {
  if (!form.studentId) { showToast('请选择请假的孩子'); return }
  if (!form.startDate || !form.endDate) { showToast('请选择请假起止日期'); return }
  if (form.endDate < form.startDate) { showToast('结束日期不能早于开始日期'); return }
  if (!form.reason.trim()) { showToast('请填写请假事由'); return }
  submitting.value = true
  try {
    const fd = new FormData()
    fd.append('studentId', String(form.studentId))
    fd.append('leaveType', form.leaveType)
    fd.append('startDate', form.startDate)
    fd.append('endDate', form.endDate)
    fd.append('reason', form.reason)
    photos.value.forEach((p) => fd.append('photos', p.file))
    await apiForm('/api/student-leave', fd)
  } finally { submitting.value = false }
  showSuccessToast('已提交，等待班主任审批')
  form.reason = ''
  photos.value.forEach((p) => URL.revokeObjectURL(p.url))
  photos.value = []
  load()
}

function askCancel(r: LeaveRow) {
  showConfirmDialog({ title: '撤回请假', message: `确定撤回 ${r.studentName} 的这条请假申请吗？` })
    .then(async () => {
      await api(`/api/student-leave/${r.id}/cancel`, { method: 'PUT' })
      showSuccessToast('已撤回')
      load()
    }).catch(() => { /* 取消 */ })
}

async function openDetail(r: LeaveRow) {
  detail.value = await api<LeaveRow>(`/api/student-leave/${r.id}`)
  detailUrls.value = []
  detailOpen.value = true
  for (const u of detail.value.photoUrls) {
    try { detailUrls.value.push(URL.createObjectURL(await fetchBlob(u))) } catch { /* 单张缺失不阻塞 */ }
  }
}

function fmtTime(t?: string) {
  return t ? t.slice(0, 16).replace('T', ' ') : '—'
}
function stLabel(s: string) {
  return ({ PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回', CANCELLED: '已撤回' } as Record<string, string>)[s] || s
}
function stClass(s: string) {
  return ({ PENDING: 'pend', APPROVED: 'ok', REJECTED: 'bad', CANCELLED: 'grey' } as Record<string, string>)[s] || ''
}

async function load() {
  rows.value = await api<LeaveRow[]>('/api/student-leave/my')
  loaded.value = true
}

onMounted(async () => {
  children.value = await api<Kid[]>('/api/parent/children')
  if (children.value.length) form.studentId = children.value[0].studentId
  load()
})
</script>

<style scoped>
.form { margin-top: 12px; padding: 12px 14px 14px; }
.only-kid { font-size: 13px; color: var(--app-text-2); margin-bottom: 10px; }
.only-kid b { color: var(--app-text-1); }
.kid-chips { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.k-chip { border: 1px solid var(--app-card-border); background: none; border-radius: 10px;
  padding: 6px 12px; font-size: 13px; color: var(--app-text-2); display: flex; align-items: baseline; gap: 5px; }
.k-chip small { font-size: 10px; color: var(--app-text-3); }
.k-chip.on { border-color: var(--shine-red); color: var(--shine-red); background: var(--shine-red-soft); }

.type-row { display: flex; gap: 8px; margin: 4px 0 10px; }
.t-chip { flex: none; border: 1px solid var(--app-card-border); background: none; border-radius: 999px;
  padding: 5px 18px; font-size: 13px; color: var(--app-text-2); }
.t-chip.on { border-color: var(--shine-red); color: var(--shine-red); background: var(--shine-red-soft); font-weight: 600; }

.date-row { display: flex; align-items: center; gap: 10px; padding: 11px 0; cursor: pointer; }
.date-row + .date-row { border-top: 1px solid var(--app-card-border); }
.date-row span { flex: none; width: 72px; font-size: 13px; color: var(--app-text-3); }
.date-row b { flex: 1; font-size: 14px; color: var(--app-text-1); font-weight: 500; }
.date-row .van-icon { color: var(--app-text-3); }
.f-in { padding: 8px 0; }

.photo-row { padding: 4px 0 2px; }
.ph-lbl { font-size: 12px; color: var(--app-text-3); margin: 4px 0 8px; }
.thumbs { display: flex; gap: 8px; flex-wrap: wrap; }
.thumb { position: relative; }
.thumb img { width: 72px; height: 72px; object-fit: cover; border-radius: 10px; display: block; }
.thumb .rm { position: absolute; top: -6px; right: -6px; width: 20px; height: 20px; border: none;
  background: rgba(0, 0, 0, 0.6); color: #fff; border-radius: 50%; display: flex;
  align-items: center; justify-content: center; font-size: 12px; }
.add { width: 72px; height: 72px; border: 1px dashed var(--app-card-border); border-radius: 10px;
  background: none; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 4px; color: var(--app-text-3); font-size: 10px; }
.add .van-icon { font-size: 20px; color: var(--shine-red); }
.hide { display: none; }
.submit { margin-top: 12px; }

.list { padding: 6px 14px; }
.row { display: flex; align-items: center; gap: 10px; padding: 12px 0; cursor: pointer; }
.row + .row { border-top: 1px solid var(--app-card-border); }
.r-body { flex: 1; min-width: 0; }
.r-title { margin: 0; font-size: 14px; font-weight: 600; color: var(--app-text-1); }
.r-sub { margin: 3px 0 0; font-size: 11px; color: var(--app-text-3);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.r-side { flex: none; display: flex; flex-direction: column; align-items: flex-end; gap: 6px; }
.st { font-size: 12px; font-weight: 600; }
.st.pend { color: #B45309; } .st.ok { color: #0D9467; } .st.bad { color: #EF4444; } .st.grey { color: var(--app-text-3); }
.cancel { border: 1px solid var(--app-card-border); background: none; border-radius: 999px;
  font-size: 11px; color: var(--app-text-2); padding: 2px 12px; }
.empty { padding: 26px 0; text-align: center; font-size: 13px; color: var(--app-text-3); }

.pop { padding-bottom: 14px; }
.p-head { padding: 16px 18px 6px; }
.p-head b { font-size: 16px; display: block; }
.p-head small { display: block; margin-top: 3px; font-size: 11px; color: var(--app-text-3); }
.p-body { padding: 0 12px; }
.desc { margin: 6px 4px; font-size: 13px; color: var(--app-text-1); line-height: 1.6; }
.big-photos { display: flex; gap: 8px; flex-wrap: wrap; }
.big-photos img { width: 100%; border-radius: 10px; display: block; }
.detail-cells { border: 1px solid var(--app-card-border); border-radius: 12px; padding: 4px 12px; }
.detail-cells p { display: flex; justify-content: space-between; gap: 12px; margin: 0; padding: 10px 0; font-size: 13px; }
.detail-cells p + p { border-top: 1px solid var(--app-card-border); }
.detail-cells span { flex: none; color: var(--app-text-3); }
.detail-cells b { text-align: right; font-weight: 600; color: var(--app-text-1); }
</style>
