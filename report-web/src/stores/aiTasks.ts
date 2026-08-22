import { defineStore } from 'pinia'
import { api } from '../api/http'
import { useAuthStore } from './auth'

/** AI 分析任务（排队/生成中/成功/失败；切页/关浏览器后端照跑，回来自动恢复展示） */
export interface AiTask {
  taskId: number
  taskType: 'COMMENT' | 'SUMMARY'
  studentId: number
  studentName?: string
  termId: number
  status: string
  source?: string
  error?: string
  queuePosition?: number
  createTime?: string
  finishedTime?: string
  /** 成功后的结果体（COMMENT: {draft,source}；SUMMARY: {raw,blocks,source}） */
  result?: Record<string, unknown>
}

/**
 * 全局 AI 任务状态：提交即返 taskId，轮询 /api/ai/tasks/mine 同步全部任务；
 * 有进行中任务时 2s 一轮，全部结束后 15s 一轮兜底（也能及时看到新提交）。
 * 页面（寄语/总结）从这里取自己关心的任务详情，切页不丢状态。
 */
export const useAiTasksStore = defineStore('aiTasks', {
  state: () => ({
    tasks: [] as AiTask[],
    timer: 0 as ReturnType<typeof setInterval> | 0,
    panelOpen: false,
  }),
  getters: {
    running: (s) => s.tasks.filter((t) => t.status === '排队' || t.status === '生成中'),
    runningCount(): number {
      return this.running.length
    },
    /** key = `${type}-${studentId}-${termId}` → 最新任务（tasks 按 id DESC，首个即最新，不覆盖） */
    byKey: (s) => {
      const m = new Map<string, AiTask>()
      for (const t of s.tasks) {
        const k = `${t.taskType}-${t.studentId}-${t.termId}`
        if (!m.has(k)) m.set(k, t)
      }
      return m
    },
  },
  actions: {
    /** 登录后/组件挂载时启动轮询；已启动则幂等 */
    start() {
      if (!useAuthStore().token) return
      this.refresh()
      if (this.timer) return
      this.timer = setInterval(() => this.refresh(), 2000)
    },
    stop() {
      if (this.timer) clearInterval(this.timer)
      this.timer = 0
      this.tasks = []
    },
    async refresh() {
      if (!useAuthStore().token) {
        this.stop() // 已退出登录：停轮询清状态，用户间不串数据
        return
      }
      try {
        const list = await api<AiTask[]>('/api/ai/tasks/mine?limit=50')
        // 排队任务附带排队位次（后端详情接口才有，这里只展示列表，位次在页面详情里查）
        this.tasks = list
      } catch {
        // 轮询失败静默（未登录/网络抖动），下一轮重试
      }
    },
    /** 提交任务（同学生同类型未完成会去重复用） */
    async submit(taskType: 'COMMENT' | 'SUMMARY', studentId: number, termId: number) {
      const d = await api<{ taskId: number }>('/api/ai/tasks', {
        method: 'POST',
        json: { type: taskType, studentId, termId },
      })
      this.refresh()
      return d.taskId
    },
    /** 查单个任务详情（含排队位次/结果体） */
    async fetchDetail(taskId: number) {
      const t = await api<AiTask>(`/api/ai/tasks/${taskId}`)
      const i = this.tasks.findIndex((x) => x.taskId === taskId)
      if (i >= 0) this.tasks[i] = { ...this.tasks[i], ...t }
      return t
    },
  },
})
