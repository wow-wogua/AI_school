import { ElMessage } from 'element-plus'
import router from '../router'
import { useAuthStore } from '../stores/auth'

export interface ApiResp<T> {
  code: number
  message: string
  data: T
}

export async function api<T>(path: string, init?: RequestInit & { json?: unknown }): Promise<T> {
  const auth = useAuthStore()
  const headers: Record<string, string> = {}
  if (auth.token) headers['Authorization'] = 'Bearer ' + auth.token
  let body = init?.body
  if (init?.json !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(init.json)
  }
  const resp = await fetch(path, { ...init, headers, body })
  if (resp.status === 401) {
    auth.logout()
    router.push('/login')
    throw new Error('未登录或登录已过期')
  }
  const r = (await resp.json()) as ApiResp<T>
  if (!resp.ok || r.code !== 0) {
    ElMessage.error(r.message || `请求失败(${resp.status})`)
    throw new Error(r.message)
  }
  return r.data
}

/** multipart 上传（浏览器自动带 boundary，勿手动设 Content-Type） */
export async function apiForm<T>(path: string, form: FormData): Promise<T> {
  const auth = useAuthStore()
  const resp = await fetch(path, {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + auth.token },
    body: form,
  })
  if (resp.status === 401) {
    auth.logout()
    router.push('/login')
    throw new Error('未登录或登录已过期')
  }
  const r = (await resp.json()) as ApiResp<T>
  if (!resp.ok || r.code !== 0) {
    ElMessage.error(r.message || `请求失败(${resp.status})`)
    throw new Error(r.message)
  }
  return r.data
}

/** 带 JWT 拉二进制（PDF 预览/下载用，iframe 带不了 Authorization 头） */
export async function fetchBlob(path: string): Promise<Blob> {
  const auth = useAuthStore()
  const resp = await fetch(path, { headers: { Authorization: 'Bearer ' + auth.token } })
  if (resp.status === 401) {
    auth.logout()
    router.push('/login')
    throw new Error('未登录或登录已过期')
  }
  if (!resp.ok) throw new Error(`下载失败(${resp.status})`)
  return resp.blob()
}
