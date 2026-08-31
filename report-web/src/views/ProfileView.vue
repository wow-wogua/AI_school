<template>
  <div class="app-page profile">
    <!-- 头区：教师照片 + 姓名（点照片可更换） -->
    <div class="app-hero hero">
      <div class="me">
        <button class="ava" type="button" @click="pickPhoto">
          <img v-if="photoSrc" :src="photoSrc" alt="教师照片">
          <span v-else>{{ avatarChar }}</span>
          <span class="ava-cam"><van-icon name="photograph" /></span>
        </button>
        <div>
          <h1>{{ form.realName }}</h1>
          <p><span class="app-chip role-chip">{{ roleLabel }}</span></p>
        </div>
      </div>
      <p class="hero-tip">完善教师档案，用于校内教师队伍建设与展示</p>
    </div>

    <!-- 基础信息 -->
    <div class="app-card overlap cells">
      <div class="app-sec card-title">基础信息</div>
      <van-field v-model="form.employeeNo" label="工号" placeholder="校内工号（唯一）" input-align="right" />
      <van-field v-model="genderText" is-link readonly label="性别" placeholder="选择"
        input-align="right" @click="genderOpen = true" />
      <van-field v-model="subjectText" is-link readonly label="任教学科" placeholder="选择"
        input-align="right" @click="subjectOpen = true" />
      <van-field v-model="form.title" is-link readonly label="职称" placeholder="选择"
        input-align="right" @click="titleOpen = true" />
      <van-field v-model="form.duty" is-link readonly label="职务" placeholder="选择"
        input-align="right" @click="dutyOpen = true" />
    </div>

    <!-- 从业信息 -->
    <div class="app-card cells">
      <div class="app-sec card-title">从业信息</div>
      <van-field v-model="form.teachingYears" type="digit" label="教龄（年）" placeholder="如 12"
        input-align="right" />
      <van-field v-model="hireText" is-link readonly label="入职年月" placeholder="选择"
        input-align="right" @click="hireOpen = true" />
      <van-field v-model="form.intro" type="textarea" rows="3" maxlength="500" show-word-limit
        label="简介" placeholder="个人教育理念、专长、荣誉等（选填）" />
    </div>

    <!-- 我的成就入口（教师风采） -->
    <div class="app-card cells honor-entry">
      <van-cell title="我的成就" icon="medal-o" is-link to="/teacher-honor/new" />
    </div>

    <!-- 吸底保存 -->
    <div class="submit-bar">
      <van-button round block type="primary" class="submit" :loading="saving"
        loading-text="正在保存…" @click="save">保存档案</van-button>
    </div>

    <p class="app-foot">石实实验学校 · 教师档案</p>

    <!-- 浏览器形态文件选择（App 形态走系统相机/相册） -->
    <input ref="fileInput" type="file" accept="image/jpeg,image/png" class="hide" @change="onFile" />

    <van-popup v-model:show="genderOpen" position="bottom" round>
      <van-picker title="性别" :columns="wrap(['男', '女'])" @confirm="onPick('gender', $event)" @cancel="genderOpen = false" />
    </van-popup>
    <van-popup v-model:show="subjectOpen" position="bottom" round>
      <van-picker title="任教学科" :columns="subjectColumns" @confirm="onPick('subjectId', $event)" @cancel="subjectOpen = false" />
    </van-popup>
    <van-popup v-model:show="titleOpen" position="bottom" round>
      <van-picker title="职称" :columns="wrap(TITLES)" @confirm="onPick('title', $event)" @cancel="titleOpen = false" />
    </van-popup>
    <van-popup v-model:show="dutyOpen" position="bottom" round>
      <van-picker title="职务" :columns="wrap(DUTIES)" @confirm="onPick('duty', $event)" @cancel="dutyOpen = false" />
    </van-popup>
    <van-popup v-model:show="hireOpen" position="bottom" round>
      <van-date-picker title="入职年月" v-model="hirePick" :columns-type="['year', 'month']"
        :min-date="minDate" :max-date="maxDate"
        @confirm="hireOpen = false" @cancel="hireOpen = false" />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { showSuccessToast, showToast } from 'vant'
import { Capacitor } from '@capacitor/core'
import { Camera, CameraResultType, CameraSource } from '@capacitor/camera'
import { api, apiForm, fetchBlob } from '../api/http'
import { useAuthStore } from '../stores/auth'

const TITLES = ['二级教师', '一级教师', '高级教师', '正高级教师', '其他']
const DUTIES = ['班主任', '年级组长', '教研组长', '备课组长', '其他']

const auth = useAuthStore()
const form = ref<any>({})
const subjects = ref<{ id: number; name: string }[]>([])
const saving = ref(false)
const fileInput = ref<HTMLInputElement>()
const photoFile = ref<File>()
const photoSrc = ref('')
let serverPhotoUrl = ''

const genderOpen = ref(false)
const subjectOpen = ref(false)
const titleOpen = ref(false)
const dutyOpen = ref(false)
const hireOpen = ref(false)
const hirePick = ref<string[]>([])

const wrap = (arr: string[]) => arr.map((t) => ({ text: t, value: t }))
const subjectColumns = computed(() => subjects.value.map((s) => ({ text: s.name, value: s.id })))
const genderText = computed(() => form.value.gender ?? '')
const subjectText = computed(() => subjects.value.find((s) => s.id === form.value.subjectId)?.name ?? '')
const hireText = computed(() => Array.isArray(hirePick.value) && hirePick.value.length === 2
  ? `${hirePick.value[0]}-${hirePick.value[1]}` : '')
const avatarChar = computed(() => form.value.realName?.charAt(0) || '师')
const roleLabel = computed(() =>
  ({ ADMIN: '管理员', HEAD_TEACHER: '班主任', TEACHER: '任课教师' }[auth.role] ?? auth.role))

const minDate = new Date(1980, 0)
const maxDate = new Date()

/** 四个选择器共用确认入口（Vant4 confirm 载荷：{selectedValues, selectedOptions}） */
function onPick(field: string, ev: { selectedValues: unknown[]; selectedOptions: { value: unknown }[] }) {
  form.value[field] = ev.selectedOptions?.[0]?.value ?? undefined
  ;({ gender: genderOpen, subjectId: subjectOpen, title: titleOpen, duty: dutyOpen } as any)[field].value = false
}

/* App 形态走系统相机/相册；浏览器形态走文件选择 */
async function pickPhoto() {
  if (Capacitor.isNativePlatform()) {
    try {
      const p = await Camera.getPhoto({
        quality: 80, width: 900,
        resultType: CameraResultType.DataUrl,
        source: CameraSource.Prompt, allowEditing: true,
      })
      const blob = await (await fetch(p.dataUrl!)).blob()
      setPhoto(new File([blob], `photo.${p.format || 'jpg'}`, { type: blob.type || 'image/jpeg' }))
    } catch { /* 用户取消 */ }
  } else {
    fileInput.value?.click()
  }
}

function onFile(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (f) setPhoto(f)
  ;(e.target as HTMLInputElement).value = ''
}

function setPhoto(f: File) {
  if (photoSrc.value.startsWith('blob:')) URL.revokeObjectURL(photoSrc.value)
  photoFile.value = f
  photoSrc.value = URL.createObjectURL(f)
}

/** 已存照片：JWT 拉流 → objectURL（<img> 带不了 Authorization 头） */
async function loadServerPhoto(url: string) {
  try {
    const blob = await fetchBlob(url)
    serverPhotoUrl = url
    photoSrc.value = URL.createObjectURL(blob)
  } catch { /* 照片加载失败保持首字占位 */ }
}

async function save() {
  if (saving.value) return
  saving.value = true
  try {
    if (photoFile.value) {
      const fd = new FormData()
      fd.append('photo', photoFile.value)
      const r = await apiForm<{ photoUrl: string }>('/api/profile/me/photo', fd)
      serverPhotoUrl = r.photoUrl
      if (photoSrc.value.startsWith('blob:')) URL.revokeObjectURL(photoSrc.value)
      await loadServerPhoto(serverPhotoUrl)
      photoFile.value = undefined
    }
    await api('/api/profile/me', {
      method: 'PUT',
      json: {
        employeeNo: form.value.employeeNo?.trim() || null,
        gender: form.value.gender ?? null,
        subjectId: form.value.subjectId ?? null,
        title: form.value.title ?? null,
        duty: form.value.duty ?? null,
        teachingYears: form.value.teachingYears ? Number(form.value.teachingYears) : null,
        intro: form.value.intro?.trim() || null,
        hireDate: hireText.value ? `${hireText.value}-01` : null,
      },
    })
    showSuccessToast('档案已保存')
  } catch (e) {
    showToast((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  subjects.value = await api<{ id: number; name: string }[]>('/api/meta/subjects')
  const d = await api<any>('/api/profile/me')
  form.value = { realName: d.realName, ...d }
  if (d.hireDate) hirePick.value = [d.hireDate.slice(0, 4), d.hireDate.slice(5, 7)]
  if (d.photoUrl) await loadServerPhoto(d.photoUrl)
})

onUnmounted(() => {
  if (photoSrc.value.startsWith('blob:')) URL.revokeObjectURL(photoSrc.value)
})
</script>

<style scoped>
.hero { padding-bottom: 52px; }
.me { display: flex; align-items: center; gap: 14px; }
.ava { position: relative; width: 66px; height: 66px; padding: 0; border: none;
  border-radius: 50%; background: rgba(255,255,255,.92); color: var(--app-blue-deep);
  font-size: 24px; font-weight: 700; cursor: pointer; overflow: visible; }
.ava img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; display: block; }
.ava-cam { position: absolute; right: -2px; bottom: -2px; display: flex; align-items: center;
  justify-content: center; width: 22px; height: 22px; border-radius: 50%;
  background: #fff; color: var(--app-blue); font-size: 13px;
  box-shadow: 0 1px 4px rgba(10,22,60,.25); }
.me h1 { margin: 0 0 6px; font-size: 20px; font-weight: 800; }
.role-chip { background: rgba(255,255,255,.2); color: #fff; }
.hero-tip { margin: 14px 0 0; font-size: 12px; color: rgba(255,255,255,.65); }

.cells { margin-top: 12px; padding: 8px 0 6px; }
.cells.overlap { margin-top: -36px; }
.card-title { padding: 6px 16px 4px; }
.cells :deep(.van-field) { padding: 11px 16px; font-size: 14px; }
.cells :deep(.van-field__label) { color: var(--app-text-2); width: 84px; }
.cells :deep(.van-field__control) { color: var(--app-text-1); }
.honor-entry { padding: 0; }
.honor-entry :deep(.van-cell) { padding: 12px 16px; font-size: 14px; }
.honor-entry :deep(.van-cell .van-icon:not(.van-cell__right-icon)) {
  color: var(--app-gold); font-size: 17px; margin-right: 2px; }

.submit-bar { position: sticky; bottom: 0; z-index: 5; margin-top: 16px;
  padding: 14px 0 calc(8px + env(safe-area-inset-bottom));
  background: linear-gradient(180deg, rgba(244,246,251,0), #F4F6FB 42%); }
.submit { height: 46px; font-size: 16px; font-weight: 600; border: none;
  background: linear-gradient(150deg, #1E3A8A, #2F5FC0); }
.hide { display: none; }
</style>
