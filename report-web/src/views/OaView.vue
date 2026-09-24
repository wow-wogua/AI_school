<template>
  <div class="app-page oa">
    <!-- 发起入口 -->
    <div class="app-card tex-b start">
      <button class="start-btn seal" type="button" @click="openNew('SEAL')">
        <van-icon name="bookmark-o" /><span>公章使用申请</span>
      </button>
      <button class="start-btn goods" type="button" @click="openNew('GOODS')">
        <van-icon name="shopping-cart-o" /><span>物资申领</span>
      </button>
      <button class="start-btn leave" type="button" @click="openNew('LEAVE')">
        <van-icon name="clock-o" /><span>教师请假</span>
      </button>
    </div>

    <van-tabs v-model:active="tab" class="oa-tabs" sticky>
      <van-tab title="我的申请">
        <div class="app-card list">
          <div v-if="!my.length" class="empty">还没有申请记录</div>
          <div v-for="f in my" :key="f.id" class="row" @click="openDetail(f.id)">
            <van-tag :type="tagOf(f.formType)" plain>{{ f.typeName }}</van-tag>
            <div class="r-body">
              <p class="r-title">{{ f.title }}</p>
              <p class="r-sub">{{ statusText(f) }} · {{ relTime(f.createTime) }}</p>
            </div>
            <span class="st" :class="stClass(f)">{{ stLabel(f) }}</span>
          </div>
        </div>
      </van-tab>
      <van-tab :title="todo.length ? `待我审批 ${todo.length}` : '待我审批'">
        <div class="app-card list">
          <div v-if="!todo.length" class="empty">暂无待审批单据</div>
          <div v-for="f in todo" :key="f.id" class="row" @click="openDetail(f.id)">
            <van-tag :type="tagOf(f.formType)" plain>{{ f.typeName }}</van-tag>
            <div class="r-body">
              <p class="r-title">{{ f.applicantName }}：{{ f.title }}</p>
              <p class="r-sub">待 {{ f.nodeName }} · {{ relTime(f.createTime) }}</p>
            </div>
            <span class="st pend">待审</span>
          </div>
        </div>
      </van-tab>
    </van-tabs>

    <!-- 发起弹层 -->
    <van-popup v-model:show="newOpen" position="bottom" round :style="{ maxHeight: '82%' }" class="pop">
      <div class="p-head">
        <b>{{ typeLabel(newType) }}</b>
        <small>{{ newType === 'SEAL' ? '提交后依次经一、二、三级审批' : '提交后按学校配置的级数审批' }}</small>
      </div>
      <div class="p-body">
        <template v-if="newType === 'SEAL'">
          <van-field v-model="newTitle" type="textarea" rows="2" autosize label="事由" placeholder="例如：学生竞赛报名表盖章"
            :rules="[{ required: true, message: '请填写事由' }]" />
          <van-field :model-value="useDate" is-link readonly label="使用日期" placeholder="选择日期（可选）"
            @click="openDate('use')" />
        </template>
        <template v-else-if="newType === 'LEAVE'">
          <van-field :model-value="leaveType" is-link readonly label="请假类型" placeholder="选择类型（必选）"
            @click="leaveOpen = true" />
          <van-field :model-value="leaveStart" is-link readonly label="开始日期" placeholder="必选"
            @click="openDate('start')" />
          <van-field :model-value="leaveEnd" is-link readonly label="结束日期" placeholder="必选"
            @click="openDate('end')" />
          <van-field v-model="newTitle" type="textarea" rows="2" autosize label="事由"
            placeholder="例如：家中急事需请假一天" />
        </template>
        <template v-else>
          <div v-for="(l, i) in newLines" :key="i" class="g-line">
            <van-field :model-value="l.name" is-link readonly label="物资" placeholder="选择物资"
              @click="pickLine = i; goodsOpen = true" />
            <van-field v-model="l.qty" type="digit" label="数量" placeholder="数量" class="qty" />
            <button v-if="newLines.length > 1" class="del" type="button" @click="newLines.splice(i, 1)">
              <van-icon name="cross" />
            </button>
          </div>
          <button class="add-line" type="button" @click="newLines.push({ goodsId: 0, name: '', qty: '' })">
            + 添加物资
          </button>
          <p v-if="!goods.length" class="goods-tip">暂无可申领物资，请管理员先在管理端添加物资字典</p>
        </template>
        <van-button round block type="primary" class="submit" :loading="submitting" @click="doSubmit">提交申请</van-button>
      </div>
    </van-popup>

    <!-- 详情弹层 -->
    <van-popup v-model:show="detailOpen" position="bottom" round :style="{ maxHeight: '86%' }" class="pop">
      <template v-if="detail">
        <div class="p-head">
          <b>{{ detail.typeName }} · {{ detail.title }}</b>
          <small>{{ detail.applicantName }} 发起于 {{ relTime(detail.createTime) }}</small>
        </div>
        <div class="p-body">
          <!-- 明细 -->
          <div class="app-sec" style="margin: 0 0 4px">申请明细</div>
          <div v-if="detail.formType === 'SEAL'" class="detail-cells">
            <p><span>使用日期</span><b>{{ detailJson.useDate || '未指定' }}</b></p>
            <p><span>事由</span><b>{{ detailJson.reason }}</b></p>
          </div>
          <div v-else-if="detail.formType === 'LEAVE'" class="detail-cells">
            <p><span>请假类型</span><b>{{ detailJson.leaveType }}</b></p>
            <p><span>起止日期</span><b>{{ detailJson.startDate }} ~ {{ detailJson.endDate }}</b></p>
            <p><span>事由</span><b>{{ detailJson.reason }}</b></p>
          </div>
          <div v-else class="detail-cells goods-cells">
            <p v-for="(l, i) in detailJson" :key="i">
              <span>{{ l.name }}</span><b>{{ l.qty }} {{ l.unit }}<small v-if="l.location"> · {{ l.location }}</small></b>
            </p>
          </div>

          <!-- 流转日志 -->
          <div class="app-sec" style="margin: 14px 0 4px">流转记录</div>
          <div class="logs">
            <div v-for="(l, i) in detail.logs" :key="i" class="log">
              <span class="l-dot" :class="actClass(l.action)"></span>
              <div class="l-body">
                <p><b>{{ l.nodeName }}</b> · {{ l.operatorName }} {{ actLabel(l.action) }}<small v-if="l.note">：{{ l.note }}</small></p>
                <small>{{ relTime(l.createTime) }}</small>
              </div>
            </div>
          </div>

          <!-- 动作区 -->
          <van-field v-if="canAct" v-model="opinion" type="textarea" rows="1" autosize label="意见"
            placeholder="审批意见（可选）" class="opinion" />
          <div v-if="canApprove" class="acts">
            <van-button round type="danger" plain @click="doHandle('REJECT')">驳回</van-button>
            <van-button round type="primary" @click="doHandle('AGREE')">通过</van-button>
          </div>
          <van-button v-else-if="canRevoke" round block plain type="default" class="submit"
            @click="doHandle('REVOKE')">撤回申请</van-button>
          <p v-if="detail.status === 'PENDING'" class="node-tip">
            当前节点：<b>{{ detail.nodeName }}</b>（共 {{ detail.levels }} 级审批）
          </p>
        </div>
      </template>
    </van-popup>

    <!-- 物资/类型/日期选择器 -->
    <van-popup v-model:show="goodsOpen" position="bottom" round>
      <van-picker title="选择物资" :columns="goodsColumns" @confirm="onGoods" @cancel="goodsOpen = false" />
    </van-popup>
    <van-popup v-model:show="leaveOpen" position="bottom" round>
      <van-picker title="请假类型" :columns="LEAVE_TYPES"
        @confirm="(ev: any) => { leaveType = ev.selectedOptions?.[0]?.text || ''; leaveOpen = false }"
        @cancel="leaveOpen = false" />
    </van-popup>
    <van-popup v-model:show="dateOpen" position="bottom" round>
      <van-date-picker :title="dateField === 'use' ? '使用日期' : dateField === 'start' ? '开始日期' : '结束日期'"
        v-model="dateBuf" :columns-type="['year', 'month', 'day']"
        :min-date="minDate" :max-date="maxDate" @confirm="onDateOk" @cancel="dateOpen = false" />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { showSuccessToast, showToast } from 'vant'
import { api } from '../api/http'
import { relTime } from '../utils/fmt'
import { useAuthStore } from '../stores/auth'

interface OaRow {
  id: number; formType: string; typeName: string; title: string; applicantName: string
  status: string; currentLevel: number; nodeName: string; createTime: string
}
interface OaDetail extends OaRow {
  detail: string; levels: number
  logs: { action: string; nodeName: string; operatorName: string; note: string; createTime: string }[]
}
interface GoodsOpt { id: number; name: string; unit: string; stock: number; location: string }

const auth = useAuthStore()
const tab = ref(0)
const my = ref<OaRow[]>([])
const todo = ref<OaRow[]>([])
const detail = ref<OaDetail | null>(null)
const detailOpen = ref(false)

async function load() {
  const [a, b] = await Promise.all([
    api<OaRow[]>('/api/oa/my'),
    api<OaRow[]>('/api/oa/todo'),
  ])
  my.value = a; todo.value = b
}

// ---- 发起 ----
const newOpen = ref(false)
const newType = ref('SEAL')
const newTitle = ref('')
const useDate = ref('')
const goods = ref<GoodsOpt[]>([])
const goodsOpen = ref(false)
const pickLine = ref(0)
const newLines = ref<{ goodsId: number; name: string; qty: string }[]>([{ goodsId: 0, name: '', qty: '' }])
const submitting = ref(false)

/* 日期三字段共用一个 picker（use=公章使用日期 / start·end=请假起止） */
const dateOpen = ref(false)
const dateField = ref<'use' | 'start' | 'end'>('use')
const dateBuf = ref<string[]>([])
const leaveType = ref('')
const leaveStart = ref('')
const leaveEnd = ref('')
const leaveOpen = ref(false)
const LEAVE_TYPES = ['事假', '病假', '婚假', '产假', '其他'].map((t) => ({ text: t, value: t }))

const minDate = new Date(2020, 0, 1)
const maxDate = new Date(2030, 11, 31)
const goodsColumns = computed(() => goods.value.map((g) => ({
  text: `${g.name}（库存 ${g.stock}${g.unit}）`, value: g.id,
})))

function typeLabel(t: string) {
  return ({ SEAL: '公章使用申请', GOODS: '物资申领', LEAVE: '教师请假' } as Record<string, string>)[t] || t
}
function tagOf(t: string) {
  return ({ SEAL: 'primary', GOODS: 'warning', LEAVE: 'success' } as Record<string, string>)[t] || 'primary'
}

function openDate(field: 'use' | 'start' | 'end') {
  dateField.value = field
  const v = field === 'use' ? useDate.value : field === 'start' ? leaveStart.value : leaveEnd.value
  dateBuf.value = v ? v.split('-') : []
  dateOpen.value = true
}
function onDateOk() {
  const v = dateBuf.value.join('-')
  if (dateField.value === 'use') useDate.value = v
  else if (dateField.value === 'start') leaveStart.value = v
  else leaveEnd.value = v
  dateOpen.value = false
}

function openNew(type: string) {
  newType.value = type
  newTitle.value = ''
  useDate.value = ''
  leaveType.value = ''
  leaveStart.value = ''
  leaveEnd.value = ''
  newLines.value = [{ goodsId: 0, name: '', qty: '' }]
  if (type === 'GOODS' && !goods.value.length) {
    api<GoodsOpt[]>('/api/oa/goods').then((d) => (goods.value = d)).catch(() => {})
  }
  newOpen.value = true
}

function onGoods(ev: { selectedOptions: { text: string; value: number }[] }) {
  const opt = ev.selectedOptions?.[0]
  if (opt && newLines.value[pickLine.value]) {
    const g = goods.value.find((x) => x.id === opt.value)
    newLines.value[pickLine.value].goodsId = opt.value
    newLines.value[pickLine.value].name = g ? g.name : opt.text
  }
  goodsOpen.value = false
}

async function doSubmit() {
  if (newType.value === 'SEAL') {
    if (!newTitle.value.trim()) { showToast('请填写事由'); return }
    submitting.value = true
    try {
      await api('/api/oa/submit', { method: 'POST', json: { formType: 'SEAL', title: newTitle.value, useDate: useDate.value } })
    } finally { submitting.value = false }
  } else if (newType.value === 'LEAVE') {
    if (!leaveType.value) { showToast('请选择请假类型'); return }
    if (!leaveStart.value || !leaveEnd.value) { showToast('请选择起止日期'); return }
    if (leaveEnd.value < leaveStart.value) { showToast('结束日期不能早于开始日期'); return }
    if (!newTitle.value.trim()) { showToast('请填写请假事由'); return }
    submitting.value = true
    try {
      await api('/api/oa/submit', {
        method: 'POST',
        json: { formType: 'LEAVE', title: newTitle.value, leaveType: leaveType.value, startDate: leaveStart.value, endDate: leaveEnd.value },
      })
    } finally { submitting.value = false }
  } else {
    const lines = newLines.value.filter((l) => l.goodsId && Number(l.qty) > 0)
    if (!lines.length) { showToast('请选择物资并填写数量'); return }
    submitting.value = true
    try {
      await api('/api/oa/submit', {
        method: 'POST',
        json: { formType: 'GOODS', goodsLines: lines.map((l) => ({ goodsId: l.goodsId, qty: Number(l.qty) })) },
      })
    } finally { submitting.value = false }
  }
  newOpen.value = false
  showSuccessToast('已提交')
  load()
}

// ---- 详情/审批 ----
const opinion = ref('')
const detailJson = computed<any>(() => {
  if (!detail.value) return {}
  try { return JSON.parse(detail.value.detail) } catch { return detail.value.formType === 'SEAL' ? {} : [] }
})
/** 管理员=任意 PENDING 可代审；普通教师=当前级审批人是自己（todo 列表成员） */
const canApprove = computed(() => {
  if (!detail.value || detail.value.status !== 'PENDING') return false
  if (auth.role === 'ADMIN') return true
  return todo.value.some((t) => t.id === detail.value!.id)
})
const canRevoke = computed(() => detail.value?.status === 'PENDING' && my.value.some((m) => m.id === detail.value!.id))
const canAct = computed(() => canApprove.value || canRevoke.value)

async function openDetail(id: number) {
  opinion.value = ''
  detail.value = await api<OaDetail>(`/api/oa/${id}`)
  detailOpen.value = true
}

async function doHandle(action: string) {
  if (!detail.value) return
  await api(`/api/oa/${detail.value.id}/handle`, { method: 'POST', json: { action, note: opinion.value.trim() } })
  showSuccessToast(action === 'AGREE' ? '已通过' : action === 'REJECT' ? '已驳回' : '已撤回')
  detailOpen.value = false
  load()
}

// ---- 展示 ----
function statusText(f: OaRow) {
  return f.status === 'PENDING' ? `${f.nodeName}中` : stLabel(f)
}
function stLabel(f: OaRow) {
  return ({ PENDING: '待审', APPROVED: '已通过', REJECTED: '已驳回', REVOKED: '已撤回' } as Record<string, string>)[f.status] || f.status
}
function stClass(f: OaRow) {
  return ({ PENDING: 'pend', APPROVED: 'ok', REJECTED: 'bad', REVOKED: 'off' } as Record<string, string>)[f.status] || ''
}
function actLabel(a: string) {
  return ({ SUBMIT: '提交了申请', AGREE: '通过', REJECT: '驳回', REVOKE: '撤回' } as Record<string, string>)[a] || a
}
function actClass(a: string) {
  return ({ SUBMIT: 's', AGREE: 'ok', REJECT: 'bad', REVOKE: 'off' } as Record<string, string>)[a] || ''
}

onMounted(load)
</script>

<style scoped>
.oa-tabs { margin-top: 12px; }
.start { display: flex; gap: 10px; padding: 14px; margin-top: -36px; }
.start-btn { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6px;
  padding: 16px 0 12px; border-radius: 14px; border: none; color: #fff; font-size: 13px; font-weight: 600; }
.start-btn .van-icon { font-size: 22px; }
.start-btn.seal { background: linear-gradient(150deg, #8C1D23, #A8232B); }
.start-btn.goods { background: linear-gradient(150deg, #B45309, #D97706); }
.start-btn.leave { background: linear-gradient(150deg, #047857, #0D9467); }

.list { margin-top: 12px; padding: 6px 14px; }
.row { display: flex; align-items: center; gap: 10px; padding: 12px 0; cursor: pointer; }
.row + .row { border-top: 1px solid var(--app-card-border); }
.r-body { flex: 1; min-width: 0; }
.r-title { margin: 0; font-size: 14px; font-weight: 600; color: var(--app-text-1);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.r-sub { margin: 3px 0 0; font-size: 11px; color: var(--app-text-3); }
.st { flex: none; font-size: 12px; font-weight: 600; }
.st.pend { color: #B45309; } .st.ok { color: #0D9467; } .st.bad { color: #EF4444; } .st.off { color: var(--app-text-3); }
.empty { padding: 26px 0; text-align: center; font-size: 13px; color: var(--app-text-3); }

.pop { padding-bottom: 14px; }
.p-head { padding: 16px 18px 6px; }
.p-head b { font-size: 16px; display: block; }
.p-head small { display: block; margin-top: 3px; font-size: 11px; color: var(--app-text-3); }
.p-body { padding: 0 12px; }

.g-line { position: relative; padding-right: 30px; }
.g-line .del { position: absolute; right: 2px; bottom: 14px; width: 26px; height: 26px;
  border: none; background: #FDECEC; color: #EF4444; border-radius: 50%; display: flex;
  align-items: center; justify-content: center; }
.add-line { display: block; width: 100%; margin: 6px 0; padding: 10px 0; border: 1px dashed var(--app-card-border);
  border-radius: 10px; background: none; font-size: 13px; color: var(--app-blue); }
.goods-tip { margin: 8px 4px; font-size: 11px; color: var(--app-text-3); }
.submit { margin: 14px 0 4px; }

.detail-cells { border: 1px solid var(--app-card-border); border-radius: 12px; padding: 4px 12px; }
.detail-cells p { display: flex; justify-content: space-between; gap: 12px; margin: 0; padding: 10px 0;
  font-size: 13px; }
.detail-cells p + p { border-top: 1px solid var(--app-card-border); }
.detail-cells span { flex: none; color: var(--app-text-3); }
.detail-cells b { text-align: right; font-weight: 600; color: var(--app-text-1); }
.detail-cells small { color: var(--app-text-3); font-weight: 400; }

.logs { padding: 2px 2px 0; }
.log { display: flex; gap: 10px; padding: 8px 0; }
.l-dot { flex: none; width: 10px; height: 10px; border-radius: 50%; margin-top: 5px; background: var(--app-text-3); }
.l-dot.s { background: var(--app-blue); } .l-dot.ok { background: #0D9467; }
.l-dot.bad { background: #EF4444; } .l-dot.off { background: var(--app-text-3); }
.l-body p { margin: 0; font-size: 13px; color: var(--app-text-1); }
.l-body b { font-weight: 600; }
.l-body small { font-size: 11px; color: var(--app-text-3); }
.l-body > small { display: block; margin-top: 2px; }
.opinion { margin-top: 10px; }
.acts { display: flex; gap: 12px; margin: 12px 0 4px; }
.acts .van-button { flex: 1; }
.node-tip { margin: 10px 4px 4px; font-size: 11px; color: var(--app-text-3); }
.node-tip b { color: var(--app-text-2); }
</style>
