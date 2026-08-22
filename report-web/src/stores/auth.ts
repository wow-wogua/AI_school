import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    realName: localStorage.getItem('realName') || '',
    role: localStorage.getItem('role') || '',
  }),
  actions: {
    set(token: string, realName: string, role: string) {
      this.token = token
      this.realName = realName
      this.role = role
      localStorage.setItem('token', token)
      localStorage.setItem('realName', realName)
      localStorage.setItem('role', role)
    },
    logout() {
      this.token = ''
      this.realName = ''
      this.role = ''
      localStorage.removeItem('token')
      localStorage.removeItem('realName')
      localStorage.removeItem('role')
    },
  },
})
