import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    realName: localStorage.getItem('realName') || '',
    role: localStorage.getItem('role') || '',
    mustChangePwd: localStorage.getItem('mustChangePwd') === '1',
  }),
  actions: {
    set(token: string, realName: string, role: string, mustChangePwd = false) {
      this.token = token
      this.realName = realName
      this.role = role
      this.mustChangePwd = mustChangePwd
      localStorage.setItem('token', token)
      localStorage.setItem('realName', realName)
      localStorage.setItem('role', role)
      if (mustChangePwd) localStorage.setItem('mustChangePwd', '1')
      else localStorage.removeItem('mustChangePwd')
    },
    /** 换发 token（改密成功）；clearPwd=true 时一并清「待改密」态 */
    refreshToken(token: string, clearPwd = false) {
      this.token = token
      localStorage.setItem('token', token)
      if (clearPwd) {
        this.mustChangePwd = false
        localStorage.removeItem('mustChangePwd')
      }
    },
    logout() {
      this.token = ''
      this.realName = ''
      this.role = ''
      this.mustChangePwd = false
      localStorage.removeItem('token')
      localStorage.removeItem('realName')
      localStorage.removeItem('role')
      localStorage.removeItem('mustChangePwd')
    },
  },
})
