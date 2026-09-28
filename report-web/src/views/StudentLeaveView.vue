<template>
  <div class="app-page leave">
    <!-- 状态/范围筛选 -->
    <div class="app-card tex-b filters">
      <div class="f-row">
        <button v-for="t in tabs" :key="t.key" type="button" class="f-chip"
          :class="{ on: status === t.key }" @click="status = t.key">{{ t.label }}</button>
        <span class="spacer"></span>
        <template v-if="scopeVisible">
          <button type="button" class="f-chip" :class="{ on: scope === 'my' }" @click="scope = 'my'">本班</button>
          <button type="button" class="f-chip" :class="{ on: scope === 'all' }" @click="scope = 'all'">全校</button>
        </template>
      </div>
      <p class="f-tip">{{ status === 'PENDING' ? '任何一位老师批准即生效；本班学生的请假优先处理' : '含已批准/已驳回/已撤回的历史记录' }}</p>
    </div>

    <!-- 请假列表 -->
    <div class="app-card list">
      <van-skeleton v-if="!loaded" :row="6" />
      <div v-else-if="!rows.length" class="empty">{{ status === 'PENDING' ? '没有待审批的请假' : '暂无请假记录' }}</div>
      <div v-for="r in rows" :key="r.id" class="row" @click="openDetail(r)">
        <div class="r-body">
          <p class="r-title">
            <b>{{ r.studentName }}</b><span class="cls">{{ r.className }}</span>
            <span class="t-chip" :class="typeClass(r.leaveType)">{{ r.leaveType }}</span>
          </p>
          <p class="r-meta">{{ r.startDate }}<template v-if="r.endDate !== r.startDate"> ~ {{ r.endDate }}</template> · {{ r.studentNo }}</p>
          <p class="r-sub">{{ r.reason }}</p>
          <p class="r-sub">
            <van-icon v-if="r.photoCount" name="photo-o" /> {{ relTime(r.createTime) }} 提交
            <template v-if="r.status !== 'PENDING' && r.approverName"> · {{ r.approverName }}{{ r.approveNote ? '：' + r.approveNote : '' }}</template>
          </p>
        </div>
        <div class="r-side">
          <span class="st" :class="stClass(r.status)">{{ stLabel(r.status) }}</span>
          <template v-if="r.status === 'PENDING'">
            <button class="btn ok" type="button" @click.stop="doApprove(r)">批准</button>
            <button class="btn bad" type="button" @click.stop="askReject(r)">驳回</button>
          </template>
        </div>
      </div>
    </div>

    <!-- 详情弹层 -->
    <van-popup v-model:show="detailOpen" position="bottom" round :style="{ maxHeight: '82%' }" class="pop">
      <template v-if="detail">
        <div class="p-head">
          <b>{{ detail.studentName }} · {{ detail.leaveType }} · {{ stLabel(detail.status) }}</b>
          <small>{{ detail.className }} · 学号 {{ detail.studentNo }}</small>
        </div>
        <div class="p-body">
          <div class="app-sec" style="margin: 0 0 4px">请假信息</div>
          <div class="detail-cells">
            <p><span>请假日期</span><b>{{ detail.startDate }} ~ {{ detail.endDate }}</b></p>
            <p><span>提交时间</span><b>{{ fmtTime(detail.createTime) }}</b></p>
          </div>
          <div class="app-sec" style="margin: 14px 0 4px">请假事由</div>
          <p class="desc">{{ detail.reason }}</p>
          <template v-if="detailUrls.length">
            <div class="app-sec" style="margin: 14px 0 4px">凭证照片（{{ detailUrls.length }}）</div>
            <div class="big-photos"><img v-for="(u, i) in detailUrls" :key="i" :src="u" alt="凭证" /></div>
          </template>
          <template v-if="detail.status !== 'PENDING'">
            <div class="app-sec" style="margin: 14px 0 4px">审批结果</div>
            <div class="detail-cells">
              <p><span>结果</span><b>{{ stLabel(detail.status) }}</b></p>
              <p v-if="detail.approverName"><span>审批人</span><b>{{ detail.approverName }}</b></p>
              <p v-if="detail.approveTime"><span>时间</span><b>{{ fmtTime(detail.approveTime) }}</b></p>
              <p v-if="detail.approveNote"><span>意见</span><b>{{ detail.approveNote }}</b></p>
              <p v-if="detail.leaveTime"><span>离校登记</span><b>{{ fmtTime(detail.leaveTime) }}</b></p>
              <p v-if="detail.returnTime"><span>返校登记</span><b>{{ fmtTime(detail.returnTime) }}</b></p>
            </div>
          </template>
        </div>
      </template>
    </van-popup>

    <!-- 驳回原因 -->
    <van-dialog v-model:show="rejectOpen" title="驳回请假" show-cancel-button
      @confirm="doReject">
      <div class="rej-body">
        <van-field v-model="rejectNote" type="textarea" rows="2" autosize placeholder="请填写驳回原因（必填，家长可见）" :maxlength="200" />
      </div>
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { showSuccessToast, showToast } from 'vant'
import { api, fetchBlob } from '../api/http'
import { relTime } from '../utils/fmt'
import { useAuthStore } from '../stores/auth'

interface LeaveRow {
  id: number; studentId: number; studentName: string; studentNo?: string; className?: string
  leaveType: string; startDate: string; endDate: string; reason: string; status: string
  approverName: string; approveNote: string; approveTime?: string
  leaveTime?: string; returnTime?: string; createTime: string
  photoCount: number; photoUrls: string[]
}

const auth = useAuthStore()
const tabs = [
  { key: 'PENDING', label: '待审批' },
  { key: '', label: '全部记录' },
]
const status = ref('PENDING')
const scope = ref<'my' | 'all'>('my')
/* ADMIN/LEADER 数据本就全量，「本班/全校」切换无意义，隐藏 */
const scopeVisible = computed(() => auth.role === 'TEACHER' || auth.role === 'HEAD_TEACHER')
const rows = ref<LeaveRow[]>([])
const loaded = ref(false)
const detail = ref<LeaveRow | null>(null)
const detailOpen = ref(false)
const detailUrls = ref<string[]>([])
const rejectOpen = ref(false)
const rejectNote = ref('')
const rejecting = ref<LeaveRow | null>(null)

async function load() {
  const qs = new URLSearchParams()
  if (status.value) qs.set('status', status.value)
  qs.set('scope', scope.value)
  rows.value = await api<LeaveRow[]>(`/api/student-leave/list?${qs}`)
  loaded.value = true
}

watch([status, scope], () => { loaded.value = false; load() })

async function doApprove(r: LeaveRow) {
  await api(`/api/student-leave/${r.id}/approve`, { method: 'PUT' })
  showSuccessToast('已批准')
  load()
}

function askReject(r: LeaveRow) {
  rejecting.value = r
  rejectNote.value = ''
  rejectOpen.value = true
}

async function doReject() {
  if (!rejectNote.value.trim()) { showToast('请填写驳回原因'); return }
  if (!rejecting.value) return
  await api(`/api/student-leave/${rejecting.value.id}/reject?note=${encodeURIComponent(rejectNote.value.trim())}`, { method: 'PUT' })
  showSuccessToast('已驳回')
  load()
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
function typeClass(t: string) {
  return ({ 病假: 'sick', 事假: 'aff', 其他: 'oth' } as Record<string, string>)[t] || 'oth'
}

onMounted(load)
</script>

<style scoped>
.filters { margin-top: 12px; padding: 10px 14px; }
.f-row { display: flex; align-items: center; gap: 8px; }
.f-chip { flex: none; border: 1px solid var(--app-card-border); background: none; border-radius: 999px;
  padding: 5px 16px; font-size: 13px; color: var(--app-text-2); }
.f-chip.on { border-color: var(--app-blue); color: #fff; background: var(--app-blue); font-weight: 600; }
.spacer { flex: 1; }
.f-tip { margin: 8px 0 0; font-size: 11px; color: var(--app-text-3); }

.list { padding: 6px 14px; }
.row { display: flex; align-items: center; gap: 10px; padding: 12px 0; cursor: pointer; }
.row + .row { border-top: 1px solid var(--app-card-border); }
.r-body { flex: 1; min-width: 0; }
.r-title { margin: 0; font-size: 14px; color: var(--app-text-1); display: flex; align-items: center; gap: 6px; }
.r-title b { font-weight: 600; }
.cls { font-size: 11px; color: var(--app-text-3); }
.t-chip { flex: none; font-size: 10px; border-radius: 6px; padding: 1px 6px; }
.t-chip.sick { background: #FDEEE2; color: #EA580C; }
.t-chip.aff { background: #E8F6EF; color: #0D9467; }
.t-chip.oth { background: #F3EAFE; color: #7C4DD8; }
.r-meta { margin: 3px 0 0; font-size: 12px; color: var(--app-text-2); }
.r-sub { margin: 3px 0 0; font-size: 11px; color: var(--app-text-3);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.r-side { flex: none; display: flex; flex-direction: column; align-items: flex-end; gap: 5px; }
.st { font-size: 12px; font-weight: 600; }
.st.pend { color: #B45309; } .st.ok { color: #0D9467; } .st.bad { color: #EF4444; } .st.grey { color: var(--app-text-3); }
.btn { border: none; border-radius: 999px; font-size: 12px; padding: 3px 14px; }
.btn.ok { background: var(--app-blue); color: #fff; }
.btn.bad { background: none; border: 1px solid #EF4444; color: #EF4444; }
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

.rej-body { padding: 10px 16px; }
</style>
