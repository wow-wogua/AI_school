/**
 * App 更新检测（两级）：
 * ① APK 更新——管理端「版本更新」页签发布新安装包后，老师端启动/手动检查时弹窗
 *   （新版本号 + 更新日志），点「立即更新」原生插件下载（进度提示）并唤起系统安装器；
 * ② 资源更新——App 页面即服务器前端（v1.0.14 起 server.url 模式），服务器重建后
 *   version.json 变化，弹窗点更新=重载页面即用新版（零 APK）。
 * 网页版浏览器不检测（刷新即最新）。
 */
import { Capacitor } from '@capacitor/core'
import { App as CapApp } from '@capacitor/app'
import { showConfirmDialog, showLoadingToast, showSuccessToast, closeToast, showToast } from 'vant'
import { api, apiBase } from '../api/http'

const isNative = Capacitor.isNativePlatform()

interface LatestInfo {
  versionCode: number
  versionName: string
  notes: string
  force: boolean
  size: number
  url: string
}

/** 本次会话是否已提示过（跳过后不再打扰，重启 App 再提醒） */
const SKIP_KEY = 'updateSkipped'

function mb(n: number): string {
  return (n / 1024 / 1024).toFixed(1)
}

/** 入口：manual=true 为「我的」页手动检查（有结论必有反馈） */
export async function checkForUpdate(manual = false): Promise<void> {
  if (!isNative) {
    if (manual) showToast('网页版无需更新，刷新页面即最新')
    return
  }
  let latest: LatestInfo | null = null
  try {
    latest = await api<LatestInfo | null>('/api/app/latest')
  } catch {
    if (manual) showToast('检查更新失败，请稍后重试')
    return
  }
  const info = await CapApp.getInfo() // {version: '1.0.14', build: '15'}——build 即 versionCode
  const build = Number(info.build || 0)
  if (latest && latest.versionCode > build) {
    const skipped = sessionStorage.getItem(SKIP_KEY)
    if (!latest.force && !manual && skipped) return
    await offerApkUpdate(latest, manual)
    return
  }
  if (manual) {
    showSuccessToast('当前已是最新版本')
    return
  }
  await checkWebUpdate()
}

/** APK 更新弹窗 → 下载（进度）→ 唤起安装 */
async function offerApkUpdate(latest: LatestInfo, manual: boolean): Promise<void> {
  const lines = [`v${latest.versionName}`]
  if (latest.notes) lines.push('', latest.notes)
  const ok = await showConfirmDialog({
    title: '发现新版本',
    message: lines.join('\n'),
    confirmButtonText: '立即更新',
    cancelButtonText: '稍后再说',
    showCancelButton: !(latest.force && !manual),
  }).then(() => true).catch(() => false)
  if (!ok) {
    // 手动检查时点「稍后」不算跳过（用户已知悉）；自动提醒跳过则本会话不再弹
    if (!manual) sessionStorage.setItem(SKIP_KEY, '1')
    return
  }
  sessionStorage.removeItem(SKIP_KEY)
  const toast = showLoadingToast({ message: '正在下载 0%', duration: 0, forbidClick: true })
  try {
    const plugin = (Capacitor.Plugins as { AppUpdate?: any }).AppUpdate
    const listener = await plugin.addListener('downloadProgress', (d: { progress: number; received: number; total: number }) => {
      toast.message = d.total > 0 ? `正在下载 ${d.progress}%（${mb(d.received)}/${mb(d.total)}MB）` : '正在下载…'
    })
    const res = await plugin.downloadAndInstall({
      // 原生插件 HttpURLConnection 只认绝对 URL；server.url 模式下 apiBase() 为空
      // （同源相对路径），须以页面 origin 兜底拼绝对地址，否则 new URL() 直接抛异常
      url: new URL(apiBase() + latest.url, window.location.origin).href,
      token: localStorage.getItem('token') || '',
    })
    listener.remove()
    closeToast()
    if (res?.needPermission) {
      showToast('已打开系统设置：请允许本应用「安装未知应用」后，回来重新点击更新')
    }
  } catch (e: any) {
    closeToast()
    showToast(e?.message || '下载失败，请稍后重试')
  }
}

/** 资源更新：服务器前端已更新（version.json 变化）→ 弹窗确认重载 */
async function checkWebUpdate(): Promise<void> {
  let builtAt = ''
  try {
    const r = await fetch('version.json', { cache: 'no-store' })
    if (!r.ok) return
    builtAt = ((await r.json()) as { builtAt?: string }).builtAt || ''
  } catch { return }
  if (!builtAt) return
  const known = localStorage.getItem('webBuiltAt')
  if (!known) {
    localStorage.setItem('webBuiltAt', builtAt) // 首次记录基线，不打扰
    return
  }
  if (known === builtAt || sessionStorage.getItem('webUpdateSkipped')) return
  const ok = await showConfirmDialog({
    title: '功能更新',
    message: '平台已更新新功能，立即更新体验？',
    confirmButtonText: '立即更新',
    cancelButtonText: '稍后再说',
  }).then(() => true).catch(() => false)
  if (ok) {
    localStorage.setItem('webBuiltAt', builtAt)
    location.reload()
  } else {
    sessionStorage.setItem('webUpdateSkipped', '1')
  }
}
