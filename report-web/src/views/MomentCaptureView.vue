<template>
  <div class="app-page capture">
    <!-- 成功态：激励展示 -->
    <div v-if="done" class="app-card done-card">
      <span class="done-icon"><van-icon name="checked" /></span>
      <img class="done-photo" :src="donePhoto" alt="">
      <h2>微光已点亮</h2>
      <p>已记入 {{ doneCount }} 位学生的成长档案，将在班级页「本周微光」与 TA 的成长档案中展示</p>
      <div class="done-acts">
        <van-button round plain type="primary" @click="reset">再拍一条</van-button>
        <van-button round type="primary"
          @click="$router.push({ path: '/moment', query: { classId: String(classId ?? '') } })">看看全部微光</van-button>
      </div>
    </div>

    <template v-else>
      <!-- ① 照片 -->
      <div class="app-card photo-card" :class="{ empty: !photoUrl }" @click="pickPhoto">
        <img v-if="photoUrl" class="preview" :src="photoUrl" alt="">
        <div v-else class="empty-photo">
          <span class="cam"><van-icon name="photograph" /></span>
          <p>点击拍照 / 选择照片</p>
          <p class="tip">记录学生此刻的闪光瞬间</p>
        </div>
        <button v-if="photoUrl" class="retake" type="button" @click.stop="pickPhoto">
          <van-icon name="replay" /> 重拍
        </button>
      </div>

      <!-- ② 场景标签 -->
      <div class="app-card sec">
        <div class="app-sec" style="margin: 0 0 10px">场景标签</div>
        <div class="tags">
          <button v-for="t in SCENE_TAGS" :key="t" class="tag" :class="{ on: sceneTag === t }"
            type="button" @click="sceneTag = t">{{ t }}</button>
        </div>
      </div>

      <!-- ③ 关联学生 -->
      <div class="app-card sec">
        <van-cell title="班级" is-link :value="curClassName || '选择班级'" @click="clsOpen = true" />
        <div class="app-sec" style="margin: 8px 0 10px">闪光学生（{{ picked.size }}）</div>
        <div class="stu-search">
          <van-icon name="search" />
          <input v-model="stuKeyword" placeholder="搜索学生姓名...">
        </div>
        <div class="stu-grid">
          <button v-for="s in filteredStudents" :key="s.id" class="stu" :class="{ on: picked.has(s.id) }"
            type="button" @click="toggle(s.id)">
            <span class="ava" :style="{ background: avaColor(s.name) }">{{ s.name.charAt(0) }}</span>
            <span class="name">{{ s.name }}</span>
            <van-icon class="check" name="checked" />
          </button>
        </div>
        <div v-if="!students.length" class="stu-none">本班暂无学生</div>
        <div v-else-if="!filteredStudents.length" class="stu-none">没有匹配的学生</div>
      </div>

      <!-- ④ 备注 -->
      <div class="app-card sec">
        <van-field v-model="note" type="textarea" rows="2" maxlength="500" show-word-limit
          placeholder="写一句此刻的亮点（选填），将汇入学生的成长素材" />
      </div>

      <!-- 吸底提交：长学生列表滚动时按钮始终可达 -->
      <div class="submit-bar">
        <van-button round block type="primary" class="submit" :disabled="!canSubmit" :loading="submitting"
          loading-text="正在点亮…" @click="submit">点亮这束微光</van-button>
        <p class="submit-tip">照片存入学校服务器，仅校内教师可见</p>
      </div>
    </template>

    <!-- 浏览器形态的文件选择（App 形态走系统相机/相册） -->
    <input ref="fileInput" type="file" accept="image/jpeg,image/png" class="hide" @change="onFile" />

    <van-popup v-model:show="clsOpen" position="bottom" round>
      <van-picker title="选择班级" :columns="clsColumns" @confirm="onCls" @cancel="clsOpen = false" />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showToast } from 'vant'
import { Capacitor } from '@capacitor/core'
import { Camera, CameraResultType, CameraSource } from '@capacitor/camera'
import { api, apiForm } from '../api/http'

/** 场景标签（与后端 t_moment.scene_tag 约定一致） */
const SCENE_TAGS = ['课堂专注', '作业优秀', '劳动实践', '艺术风采', '运动健将', '助人为乐', '文明礼仪', '进步之星']

const route = useRoute()
const photoFile = ref<File>()
const photoUrl = ref('')
const sceneTag = ref('')
const note = ref('')
const picked = ref(new Set<number>())
const submitting = ref(false)
const done = ref(false)
const donePhoto = ref('')
const doneCount = ref(0)

const classes = ref<{ id: number; name: string }[]>([])
const classId = ref<number>()
const students = ref<{ id: number; name: string }[]>([])
const clsOpen = ref(false)
const fileInput = ref<HTMLInputElement>()

const clsColumns = computed(() => classes.value.map((c) => ({ text: c.name, value: c.id })))
const curClassName = computed(() => classes.value.find((c) => c.id === classId.value)?.name)
const stuKeyword = ref('')
const filteredStudents = computed(() => {
  const k = stuKeyword.value.trim()
  return k ? students.value.filter((s) => s.name.includes(k)) : students.value
})
const canSubmit = computed(() => !!photoFile.value && !!sceneTag.value && picked.value.size > 0)

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

function toggle(id: number) {
  const s = new Set(picked.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  picked.value = s
}

function onCls({ value }: { value: number }) {
  if (classId.value !== value) {
    classId.value = value
    picked.value = new Set()
    loadStudents()
  }
  clsOpen.value = false
}

async function loadStudents() {
  if (!classId.value) return
  const d = await api<{ records: { id: number; name: string }[] }>(
    `/api/student/list?classId=${classId.value}&page=1&size=100`)
  students.value = d.records
}

async function submit() {
  if (!canSubmit.value || submitting.value) return
  submitting.value = true
  try {
    const form = new FormData()
    form.append('photo', photoFile.value!)
    form.append('classId', String(classId.value))
    form.append('sceneTag', sceneTag.value)
    if (note.value.trim()) form.append('note', note.value.trim())
    for (const id of picked.value) form.append('studentIds', String(id))
    await apiForm('/api/moment', form)
    donePhoto.value = photoUrl.value
    doneCount.value = picked.value.size
    done.value = true
  } catch (e) {
    showToast((e as Error).message || '提交失败')
  } finally {
    submitting.value = false
  }
}

/** 再拍一条：清空全部选择（班级保留） */
function reset() {
  done.value = false
  if (photoUrl.value && photoUrl.value !== donePhoto.value) URL.revokeObjectURL(photoUrl.value)
  photoFile.value = undefined
  photoUrl.value = ''
  sceneTag.value = ''
  note.value = ''
  stuKeyword.value = ''
  picked.value = new Set()
}

const palette = ['#2F5FC0', '#7C4DD8', '#0D9467', '#B07A1C', '#D6567A', '#3A7CA5']
function avaColor(name: string) {
  let h = 0
  for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) % 997
  return palette[h % palette.length]
}

onMounted(async () => {
  classes.value = await api<{ id: number; name: string }[]>('/api/meta/my-classes')
  const fromQuery = Number(route.query.classId)
  classId.value = classes.value.find((c) => c.id === fromQuery)?.id ?? classes.value[0]?.id
  await loadStudents()
})
</script>

<style scoped>
/* 照片卡：空态虚线引导，有图全幅预览 + 重拍角钮 */
.photo-card { position: relative; overflow: hidden; min-height: 220px;
  display: flex; align-items: center; justify-content: center; cursor: pointer; }
.photo-card.empty { border: 1.5px dashed #B9C8E4; background: #F7F9FD; }
.preview { width: 100%; max-height: 340px; object-fit: cover; display: block; }
.empty-photo { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 40px 0; }
.cam { display: flex; align-items: center; justify-content: center; width: 56px; height: 56px;
  border-radius: 50%; background: linear-gradient(150deg, #1E3A8A, #2F5FC0);
  color: #fff; font-size: 26px; box-shadow: 0 6px 16px rgba(30,58,138,.3); }
.empty-photo p { margin: 0; font-size: 14px; color: var(--app-text-2); }
.empty-photo .tip { font-size: 12px; color: var(--app-text-3); }
.retake { position: absolute; right: 12px; bottom: 12px; display: flex; align-items: center; gap: 4px;
  padding: 6px 14px; border: none; border-radius: 999px; background: rgba(13,22,50,.6);
  color: #fff; font-size: 12px; cursor: pointer; backdrop-filter: blur(4px); }

.sec { margin-top: 12px; padding: 14px 14px 12px; }

/* 场景标签 chips */
.tags { display: flex; flex-wrap: wrap; gap: 8px; }
.tag { padding: 7px 16px; border: 1px solid var(--app-card-border); border-radius: 999px;
  background: #fff; color: var(--app-text-2); font-size: 13px; cursor: pointer;
  -webkit-tap-highlight-color: transparent; }
.tag.on { border-color: var(--app-blue); background: #EAF0FE; color: var(--app-blue); font-weight: 600; }

/* 学生多选宫格 */
.stu-grid { display: flex; flex-wrap: wrap; gap: 10px 8px; }
.stu { position: relative; display: flex; align-items: center; gap: 6px; padding: 6px 12px 6px 6px;
  border: 1.5px solid var(--app-card-border); border-radius: 999px; background: #fff;
  cursor: pointer; -webkit-tap-highlight-color: transparent; }
.stu .ava { display: flex; align-items: center; justify-content: center; width: 26px; height: 26px;
  border-radius: 50%; color: #fff; font-size: 12px; font-weight: 600; }
.stu .name { font-size: 13px; color: var(--app-text-1); }
.stu .check { display: none; font-size: 14px; color: var(--app-blue); }
.stu.on { border-color: var(--app-blue); background: #EAF0FE; }
.stu.on .check { display: block; }
.stu-none { padding: 14px 0 6px; font-size: 13px; color: var(--app-text-3); }
.stu-search { display: flex; align-items: center; gap: 6px; margin-bottom: 10px; padding: 7px 12px;
  border-radius: 999px; background: #F2F5FB; color: var(--app-text-3); }
.stu-search input { flex: 1; border: none; outline: none; background: transparent;
  font-size: 13px; color: var(--app-text-1); }
.stu-search input::placeholder { color: var(--app-text-3); }

/* 吸底提交：渐隐承接页面背景，长列表滚动时按钮始终可达 */
.submit-bar { position: sticky; bottom: 0; z-index: 5; margin-top: 16px;
  padding: 14px 0 calc(8px + env(safe-area-inset-bottom));
  background: linear-gradient(180deg, rgba(244,246,251,0), #F4F6FB 42%); }
.submit { height: 46px; font-size: 16px; font-weight: 600; border: none;
  background: linear-gradient(150deg, #1E3A8A, #2F5FC0); }
.submit-tip { margin: 8px 0 0; text-align: center; font-size: 11px; color: var(--app-text-3); }

/* 成功激励卡 */
.done-card { display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 30px 22px; }
.done-icon { display: flex; align-items: center; justify-content: center; width: 56px; height: 56px;
  border-radius: 50%; background: #E8F6EF; color: #0D9467; font-size: 28px; }
.done-photo { width: 100%; height: 180px; object-fit: cover; border-radius: 12px; }
.done-card h2 { margin: 4px 0 0; font-size: 19px; font-weight: 800; color: var(--app-text-1); }
.done-card p { margin: 0; font-size: 13px; line-height: 1.7; color: var(--app-text-2); text-align: center; }
.done-acts { display: flex; gap: 12px; margin-top: 10px; }
.done-acts .van-button { height: 40px; padding: 0 22px; }
.hide { display: none; }
</style>
