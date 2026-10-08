<template>
  <!-- 批37 生态补缺：App 内使用帮助——使用手册 FAQ 提炼版，按登录角色分流；
       完整操作细节仍以《使用手册》PDF（管理端分发）为准 -->
  <div class="app-page help">
    <div class="app-sec">{{ isParent ? '家长端' : '教师端' }} · 常见问题</div>
    <div class="app-card tex-b faq">
      <van-collapse v-model="active">
        <van-collapse-item v-for="(f, i) in faqs" :key="i" :title="f.q" :name="String(i)">
          <p class="ans">{{ f.a }}</p>
        </van-collapse-item>
      </van-collapse>
    </div>

    <div class="app-sec">遇到问题</div>
    <div class="app-card tex-e tip">
      <p>可在「我的 → 意见反馈」提交问题，或联系学校管理员处理；忘记密码请联系管理员（家长联系班主任）重置。</p>
    </div>
    <p class="app-foot">石实实验学校 · 石实SHINE</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const isParent = computed(() => auth.role === 'PARENT')
const active = ref<string[]>([])

/* 领导/管理员走教师壳（批3.5 教师化），看教师 FAQ */
const teacherFaqs = [
  { q: '功能入口在哪里找？', a: '首页快捷功能按用途分五区：评价记录（微光信箱、成绩管理、日常评价、学生请假、谈心记录）、成长激励（成长银行、文明班打分、德育规范）、报告档案（成长报告、期末评语、学生时间轴、荣誉证书、活动管理）、校园事务（行政办公、通知公告、扬长课程、报修、宿舍查询）、我的成长（教师档案、教师风采、成长足迹）。' },
  { q: '底部「生成中心」是什么？', a: 'AI 任务进度页：成长报告、期末评语等生成任务的进行中/已完成状态都在这里，完成后可直接下载 PDF；历史报告也可从首页「成长报告」查看。' },
  { q: '首页铃铛「消息通知」是什么？', a: '待办与提醒：待我审批、请假/审批结果、系统消息等，点击通知可直达对应处理页；有未读时铃铛显示红点。' },
  { q: '「期末评语」包含哪些内容？', a: '班主任寄语、成长总结、综合素质三段合一，点页签切换，选中学生后各段独立填写或 AI 生成。' },
  { q: '成长银行怎么用？', a: '选学生后查看双账本（操行分、能量币）与流水；学生在校凭表现累积，教师可在货架代学生兑换学校文创并现场核销。' },
  { q: '通知公告在哪里看？', a: '首页「校园事务 → 通知公告」，查看学校发布的通知公告全文（由管理员在管理端发布）。' },
  { q: '扬长课程在哪里看？', a: '首页「校园事务 → 扬长课程」，查看学校发布的课程与学习资料（图文/视频）；内容由学校管理员在管理端上传维护。' },
  { q: '忘记密码怎么办？', a: '联系学校管理员重置；重置或首次登录后会强制修改密码，改完才能继续使用。' },
]
const parentFaqs = [
  { q: '成长档案是什么？', a: '孩子在校全期的成长记录流（评价、荣誉、活动、微光时刻等）；从首页「成长档案」或孩子卡进入，多孩家长从首页孩子卡切换。' },
  { q: '成长报告和成长档案有什么区别？', a: '成长报告是按学期生成的正式 PDF 报告（家长版不含成绩）；成长档案是日常记录的实时汇总。' },
  { q: '孩子要请假怎么办理？', a: '请通过微信或电话联系班主任办理（家长端不直接提交）；审批通过后 App 会收到通知，「请假记录」可查历史。' },
  { q: '微光信箱是什么？', a: '家长拍照记录孩子的闪光时刻，上传后进入孩子的成长档案（家长和班主任可见）。' },
  { q: '扬长课程是什么？', a: '底部导航「扬长课程」：学校发布的课程与家庭教育学习资料（图文/视频），由学校管理员上传维护。' },
  { q: '换手机号了怎么办？', a: '「我的 → 修改手机号」，输入新号并确认密码即换绑；家长账号登录名就是手机号，换绑后请用新手机号登录。' },
  { q: '忘记密码怎么办？', a: '请联系孩子的班主任重置。' },
]
const faqs = computed(() => (isParent.value ? parentFaqs : teacherFaqs))
</script>

<style scoped>
.faq { padding: 4px 0; }
.faq :deep(.van-collapse-item__title) { font-size: 14px; font-weight: 600; color: var(--app-text-1); }
.ans { margin: 0; font-size: 13px; line-height: 1.8; color: var(--app-text-2); }
.tip { padding: 14px 16px; }
.tip p { margin: 0; font-size: 13px; line-height: 1.8; color: var(--app-text-2); }
</style>
