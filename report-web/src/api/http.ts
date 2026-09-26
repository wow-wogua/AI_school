import { ElMessage } from 'element-plus'
import router from '../router'
import { useAuthStore } from '../stores/auth'

export interface ApiResp<T> {
  code: number
  message: string
  data: T
}

/**
 * API 基址：App 形态直连服务器（登录页可设置，存 localStorage），
 * 打包时可注入 VITE_API_BASE 作默认值；浏览器形态留空 = 相对路径
 * （dev 走 vite 代理 / 线上 nginx 同源）。
 */
export function apiBase(): string {
  const saved = localStorage.getItem('serverBase')
  if (saved) return saved.replace(/\/+$/, '')
  return ((import.meta.env.VITE_API_BASE as string) || '').replace(/\/+$/, '')
}

/** 带 HTTP 状态码的错误（调用方可按 status 区分网络失败/密码错/限流） */
function httpError(message: string, status: number): Error & { status: number } {
  const e = new Error(message) as Error & { status: number }
  e.status = status
  return e
}

/** 网络层失败统一转中文（断网/服务器不可达/网关返回非 JSON 时浏览器抛「Failed to fetch」
    之类的英文原生错误，直接透出给用户很不专业）。status=0 供调用方识别网络层失败 */
const NET_MSG = '网络连接失败，请检查网络后重试'
function netError(): Error & { status: number } {
  return httpError(NET_MSG, 0)
}

/** 请求超时（fetch 默认无超时，经代理黑洞的挂起连接会让按钮转圈不止） */
const TIMEOUT_MSG = '请求超时，请检查网络后重试'
function isTimeout(e: unknown): boolean {
  return (e as DOMException)?.name === 'TimeoutError'
}

export async function api<T>(path: string, init?: RequestInit & { json?: unknown; timeoutMs?: number }): Promise<T> {
  const auth = useAuthStore()
  const headers: Record<string, string> = {}
  if (auth.token) headers['Authorization'] = 'Bearer ' + auth.token
  let body = init?.body
  if (init?.json !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(init.json)
  }
  let resp: Response
  try {
    resp = await fetch(apiBase() + path, { ...init, headers, body, signal: AbortSignal.timeout(init?.timeoutMs ?? 30_000) })
  } catch (e) {
    if (isTimeout(e)) throw httpError(TIMEOUT_MSG, 0)
    throw netError()
  }
  if (resp.status === 401) {
    auth.logout()
    router.push('/login')
    throw httpError('未登录或登录已过期', 401)
  }
  let r: ApiResp<T>
  try {
    r = (await resp.json()) as ApiResp<T>
  } catch {
    throw netError() // 502/504 网关错误页等非 JSON 响应
  }
  // 首登待改密（本地标志被清但 token 仍带 mcp，如多标签页场景）：补设标志并引到改密页
  if (resp.status === 403 && r.message && r.message.includes('修改初始密码')) {
    auth.mustChangePwd = true
    localStorage.setItem('mustChangePwd', '1')
    router.push('/change-password')
    throw httpError(r.message, 403)
  }
  if (!resp.ok || r.code !== 0) {
    ElMessage.error(r.message || `请求失败(${resp.status})`)
    throw httpError(r.message || `请求失败(${resp.status})`, resp.status)
  }
  return r.data
}

/** multipart 上传（浏览器自动带 boundary，勿手动设 Content-Type）。
    默认 600s：名册整校导入 xlsx 服务端要跑数分钟（nginx 同上限），照片/APK 上传也在此内 */
export async function apiForm<T>(path: string, form: FormData): Promise<T> {
  const auth = useAuthStore()
  let resp: Response
  try {
    resp = await fetch(apiBase() + path, {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + auth.token },
      body: form,
      signal: AbortSignal.timeout(600_000),
    })
  } catch (e) {
    if (isTimeout(e)) throw httpError(TIMEOUT_MSG, 0)
    throw netError()
  }
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
  let resp: Response
  try {
    resp = await fetch(apiBase() + path, { headers: { Authorization: 'Bearer ' + auth.token }, signal: AbortSignal.timeout(120_000) })
  } catch (e) {
    if (isTimeout(e)) throw httpError(TIMEOUT_MSG, 0)
    throw netError()
  }
  if (resp.status === 401) {
    auth.logout()
    router.push('/login')
    throw new Error('未登录或登录已过期')
  }
  if (!resp.ok) throw new Error(`下载失败(${resp.status})`)
  return resp.blob()
}
