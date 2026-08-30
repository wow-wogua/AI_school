/**
 * 文件的预览/保存统一入口（App 壳与浏览器行为自动分流）。
 * Android WebView 与 Chrome 是两套东西：不带 PDF 渲染器（iframe 白屏）、
 * 无视 <a download>（点击无反应）、window.open(blob:) 也开不出来——网页那套
 * 在 App 壳里全灭。原生形态统一改走「写入应用缓存 + 系统分享面板」：
 * 选 WPS/浏览器等查看器=预览，选「保存到文件」=存到手机，选微信=直接转发。
 */
import { Capacitor } from '@capacitor/core'
import { Directory, Filesystem } from '@capacitor/filesystem'
import { Share } from '@capacitor/share'
import { ElMessage } from 'element-plus'

export const isNative = Capacitor.isNativePlatform()

/** 原生：写缓存 + 调系统分享面板（失败弹提示，绝不静默） */
async function shareNative(blob: Blob, filename: string): Promise<void> {
  try {
    const name = withExt(filename, blob.type)
    const base64 = await blobToBase64(blob)
    const res = await Filesystem.writeFile({ path: name, data: base64, directory: Directory.Cache })
    await Share.share({ title: name, url: res.uri, dialogTitle: '打开或保存' })
  } catch {
    ElMessage.error('调起系统面板失败，请重试')
  }
}

/** 保存文件：浏览器=触发下载；App=系统分享面板（面板里选「保存到文件」） */
export async function saveFile(blob: Blob, filename: string): Promise<void> {
  if (isNative) return shareNative(blob, filename)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

/** 预览文件：浏览器=新标签页打开（自带查看器）；App=系统分享面板 */
export async function openFile(blob: Blob, filename: string): Promise<void> {
  if (isNative) return shareNative(blob, filename)
  window.open(URL.createObjectURL(blob))
}

/** 分享面板按扩展名识别文件类型：无后缀时按 MIME 补一个 */
function withExt(filename: string, mime: string): string {
  if (/\.[a-z0-9]{2,4}$/i.test(filename)) return filename
  const ext: Record<string, string> = {
    'image/jpeg': '.jpg', 'image/png': '.png', 'image/webp': '.webp',
    'application/pdf': '.pdf',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': '.xlsx',
  }
  return filename + (ext[mime] ?? '.bin')
}

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const r = new FileReader()
    r.onload = () => resolve((r.result as string).split(',')[1] ?? '')
    r.onerror = () => reject(new Error('文件读取失败'))
    r.readAsDataURL(blob)
  })
}
