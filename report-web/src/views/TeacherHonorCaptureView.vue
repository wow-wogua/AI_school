<template>
  <div class="app-page capture">
    <!-- 成功态：激励展示 -->
    <div v-if="done" class="app-card tl gold done-card">
      <span class="done-icon"><van-icon name="medal-o" /></span>
      <img class="done-photo" :src="donePhoto" alt="">
      <h2>成就已记录</h2>
      <p>已收入你的教师档案，全校同事可在「教师风采」看到这份荣誉</p>
      <div class="done-acts">
        <van-button round plain type="primary" @click="reset">再记一条</van-button>
        <van-button round type="primary" @click="$router.push('/teacher-honor')">去教师风采看看</van-button>
      </div>
    </div>

    <template v-else>
      <!-- ① 证书照片 -->
      <div class="app-card photo-card" :class="{ empty: !photoUrl }" @click="pickPhoto">
        <img v-if="photoUrl" class="preview" :src="photoUrl" alt="">
        <div v-else class="empty-photo">
          <span class="cam"><van-icon name="photograph" /></span>
          <p>点击拍照 / 选择证书照片</p>
          <p class="tip">jpg/png，不超过 10MB</p>
        </div>
        <button v-if="photoUrl" class="retake" type="button" @click.stop="pickPhoto">
          <van-icon name="replay" /> 重选
        </button>
      </div>

      <!-- ② 成就信息 -->
      <div class="app-card sec form">
        <div class="app-sec" style="margin: 0 0 4px">成就信息</div>
        <van-field v-model="name" label="奖项名称" placeholder="必填，如：市级优秀教师" maxlength="50" />
        <van-field v-model="levelText" is-link readonly label="级别" placeholder="选填"
          @click="levelOpen = true" />
        <van-field v-model="issuer" label="颁发单位" placeholder="选填" maxlength="100" />
        <van-field v-model="dateText" is-link readonly label="获奖日期" placeholder="选填"
          @click="dateOpen = true" />
      </div>

      <!-- ③ 我的成就（上传后即见，可删） -->
      <div class="app-card sec mine">
        <div class="app-sec" style="margin: 0 0 4px">我的成就（{{ mine.length }}）</div>
        <div v-for="h in mine" :key="h.id" class="mine-row" :data-id="h.id">
          <div class="mine-ph" @click="previewMine(h)">
            <MomentPhoto :url="h.photoUrl" />
          </div>
          <div class="mine-info">
            <p class="m-name">{{ h.name }}</p>
            <p class="m-meta">{{ h.level || '未填级别' }}<template v-if="h.honorDate"> · {{ h.honorDate }}</template></p>
          </div>
          <button class="m-del" type="button" aria-label="删除" @click="askDel(h)">
            <van-icon name="delete-o" />
          </button>
        </div>
        <div v-if="!mine.length" class="mine-none">还没有记录，上传第一份成就吧</div>
      </div>

      <!-- 吸底提交 -->
      <div class="submit-bar">
        <van-button round block type="primary" class="submit" :disabled="!canSubmit" :loading="submitting"
          loading-text="正在记录…" @click="submit">记录这份成就</van-button>
        <p class="submit-tip">证书照片存入学校服务器，「教师风采」全校可见</p>
      </div>
    </template>

    <!-- 浏览器形态的文件选择（App 形态走系统相机/相册） -->
    <input ref="fileInput" type="file" accept="image/jpeg,image/png" class="hide" @change="onFile" />

    <van-popup v-model:show="levelOpen" position="bottom" round>
      <van-picker title="级别" :columns="levelColumns" @confirm="onLevel" @cancel="levelOpen = false" />
    </van-popup>
    <van-popup v-model:show="dateOpen" position="bottom" round>
      <van-date-picker title="获奖日期" v-model="datePick" :columns-type="['year', 'month', 'day']"
        :min-date="minDate" :max-date="maxDate"
        @confirm="onDate" @cancel="dateOpen = false" />
    </van-popup>

    <!-- 删除确认 -->
    <van-dialog v-model:show="delOpen" title="删除成就" :message="`删除「${delTarget?.name || ''}」？`"
      show-cancel-button @confirm="doDel" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { showImagePreview, showToast } from 'vant'
import { Capacitor } from '@capacitor/core'
import { Camera, CameraResultType, CameraSource } from '@capacitor/camera'
import { api, apiForm } from '../api/http'
import MomentPhoto from '../components/MomentPhoto.vue'

const LEVELS = ['国家级', '省级', '市级', '区级', '校级']

const photoFile = ref<File>()
const photoUrl = ref('')
const name = ref('')
const level = ref('')
const issuer = ref('')
const datePick = ref<string[]>([])
const submitting = ref(false)
const done = ref(false)
const donePhoto = ref('')

const levelOpen = ref(false)
const dateOpen = ref(false)
const fileInput = ref<HTMLInputElement>()

const levelColumns = LEVELS.map((t) => ({ text: t, value: t }))
const levelText = computed(() => level.value)
const dateText = computed(() => datePick.value.length === 3 ? datePick.value.join('-') : '')
const canSubmit = computed(() => !!photoFile.value && !!name.value.trim())

const minDate = new Date(1980, 0, 1)
const maxDate = new Date()

const mine = ref<{ id: number; name: string; level?: string; honorDate?: string; photoUrl: string }[]>([])
const delOpen = ref(false)
const delTarget = ref<{ id: number; name: string } | null>(null)

/* App 形态走系统相机/相册弹窗；浏览器形态走文件选择 */
async function pickPhoto() {
  if (Capacitor.isNativePlatform()) {
    try {
      const p = await Camera.getPhoto({
        quality: 80, width: 1600,
        resultType: CameraResultType.DataUrl,
        source: CameraSource.Prompt, allowEditing: false,
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
  ;(e.target as HTMLInputElement).value = '' // 允许重选同一张
}

function setPhoto(f: File) {
  if (photoUrl.value) URL.revokeObjectURL(photoUrl.value)
  photoFile.value = f
  photoUrl.value = URL.createObjectURL(f)
}

/** Vant4 confirm 载荷：{selectedValues, selectedOptions, selectedIndexes}，取值走 selectedOptions */
function onLevel(ev: { selectedOptions?: { value: string }[] }) {
  level.value = ev.selectedOptions?.[0]?.value ?? ''
  levelOpen.value = false
}
function onDate(ev: { selectedValues?: string[] }) {
  datePick.value = ev.selectedValues ?? []
  dateOpen.value = false
}

async function loadMine() {
  mine.value = await api<typeof mine.value>('/api/teacher-honor/my')
}

async function submit() {
  if (!canSubmit.value || submitting.value) return
  submitting.value = true
  try {
    const form = new FormData()
    form.append('photo', photoFile.value!)
    form.append('name', name.value.trim())
    if (level.value) form.append('level', level.value)
    if (issuer.value.trim()) form.append('issuer', issuer.value.trim())
    if (dateText.value) form.append('honorDate', dateText.value)
    await apiForm('/api/teacher-honor', form)
    donePhoto.value = photoUrl.value
    done.value = true
    loadMine()
  } catch (e) {
    showToast((e as Error).message || '提交失败')
  } finally {
    submitting.value = false
  }
}

/** 再记一条：清空全部选择 */
function reset() {
  done.value = false
  if (photoUrl.value && photoUrl.value !== donePhoto.value) URL.revokeObjectURL(photoUrl.value)
  photoFile.value = undefined
  photoUrl.value = ''
  name.value = ''
  level.value = ''
  issuer.value = ''
  datePick.value = []
}

function previewMine(h: { id: number }) {
  // 取已渲染的 objectURL（MomentPhoto 组件负责带鉴权拉取）
  const el = document.querySelector<HTMLElement>(`.mine-row[data-id="${h.id}"] img`)
  if (el?.src) showImagePreview({ images: [el.src] })
}

function askDel(h: { id: number; name: string }) {
  delTarget.value = h
  delOpen.value = true
}

async function doDel() {
  if (!delTarget.value) return
  try {
    await api(`/api/teacher-honor/${delTarget.value.id}`, { method: 'DELETE' })
    showToast('已删除')
    await loadMine()
  } catch (e) {
    showToast((e as Error).message || '删除失败')
  } finally {
    delTarget.value = null
  }
}

onMounted(loadMine)

onUnmounted(() => {
  if (photoUrl.value) URL.revokeObjectURL(photoUrl.value)
})
</script>

<style scoped>
/* 照片卡：空态虚线引导，有图全幅预览 + 重选角钮（同微光拍摄） */
.photo-card { position: relative; overflow: hidden; min-height: 220px;
  display: flex; align-items: center; justify-content: center; cursor: pointer; }
.photo-card.empty { border: 1.5px dashed #D9C9A6; background: #FFFDF8; }
.preview { width: 100%; max-height: 340px; object-fit: cover; display: block; }
.empty-photo { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 40px 0; }
.cam { display: flex; align-items: center; justify-content: center; width: 56px; height: 56px;
  border-radius: 50%; background: linear-gradient(150deg, #B07A1C, #8F5E10);
  color: #fff; font-size: 26px; box-shadow: 0 6px 16px rgba(176,122,28,.3); }
.empty-photo p { margin: 0; font-size: 14px; color: var(--app-text-2); }
.empty-photo .tip { font-size: 12px; color: var(--app-text-3); }
.retake { position: absolute; right: 12px; bottom: 12px; display: flex; align-items: center; gap: 4px;
  padding: 6px 14px; border: none; border-radius: 999px; background: rgba(13,22,50,.6);
  color: #fff; font-size: 12px; cursor: pointer; backdrop-filter: blur(4px); }

.sec { margin-top: 12px; padding: 14px 14px 10px; }
.form :deep(.van-field) { padding: 10px 2px; font-size: 14px; }
.form :deep(.van-field__label) { color: var(--app-text-2); width: 76px; }

/* 我的成就管理区 */
.mine-row { display: flex; align-items: center; gap: 12px; padding: 10px 0; }
.mine-row + .mine-row { border-top: 1px solid var(--app-card-border); }
.mine-ph { flex: none; width: 46px; height: 60px; border-radius: 8px; overflow: hidden;
  background: #F2F5FB; cursor: pointer; }
.mine-info { flex: 1; min-width: 0; }
.m-name { margin: 0; font-size: 14px; font-weight: 600; color: var(--app-text-1);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.m-meta { margin: 3px 0 0; font-size: 12px; color: var(--app-text-3); }
.m-del { flex: none; display: flex; align-items: center; justify-content: center;
  width: 34px; height: 34px; border: none; border-radius: 50%; background: none;
  color: #C6CDD9; font-size: 17px; cursor: pointer; }
.m-del:active { color: #EF4444; }
.mine-none { padding: 14px 0 8px; font-size: 13px; color: var(--app-text-3); }

/* 吸底提交（同微光拍摄） */
.submit-bar { position: sticky; bottom: 0; z-index: 5; margin-top: 16px;
  padding: 14px 0 calc(8px + env(safe-area-inset-bottom));
  background: linear-gradient(180deg, rgba(244,246,251,0), #F4F6FB 42%); }
.submit { height: 46px; font-size: 16px; font-weight: 600; border: none;
  background: linear-gradient(150deg, #B07A1C, #8F5E10); }
.submit-tip { margin: 8px 0 0; text-align: center; font-size: 11px; color: var(--app-text-3); }

/* 成功激励卡 */
.done-card { display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 30px 22px; }
.done-icon { display: flex; align-items: center; justify-content: center; width: 56px; height: 56px;
  border-radius: 50%; background: #FBF3DF; color: #B07A1C; font-size: 28px; }
.done-photo { width: 100%; height: 180px; object-fit: cover; border-radius: 12px; }
.done-card h2 { margin: 4px 0 0; font-size: 19px; font-weight: 800; color: var(--app-text-1); }
.done-card p { margin: 0; font-size: 13px; line-height: 1.7; color: var(--app-text-2); text-align: center; }
.done-acts { display: flex; gap: 12px; margin-top: 10px; }
.done-acts .van-button { height: 40px; padding: 0 22px; }
.done-acts .van-button--primary { background: linear-gradient(150deg, #B07A1C, #8F5E10); border: none; }
.hide { display: none; }
</style>
