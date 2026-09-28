import { defineStore } from 'pinia'
import { api } from '../api/http'
import { useAuthStore } from './auth'

/**
 * 通知中心未读角标（批29）：登录后 2 分钟一轮拉未读数（GUARD 无通知中心跳过），
 * 「我的」页角标与通知中心页共用；静默失败不打扰。
 */
export const useNotifyStore = defineStore('notify', {
  state: () => ({
    unread: 0,
    timer: 0 as ReturnType<typeof setInterval> | 0,
  }),
  actions: {
    async refresh() {
      const auth = useAuthStore()
      if (!auth.token || auth.role === 'GUARD') return
      try {
        const d = await api<{ unread: number }>('/api/notification/unread-count')
        this.unread = d.unread
      } catch { /* 静默 */ }
    },
    start() {
      this.refresh()
      if (this.timer) return
      this.timer = setInterval(() => this.refresh(), 120_000)
    },
    stop() {
      if (this.timer) clearInterval(this.timer)
      this.timer = 0
      this.unread = 0
    },
  },
})
