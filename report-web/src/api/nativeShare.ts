/**
 * 原生形态（安卓 App 壳）的 PDF 预览/保存：
 * Android WebView 不带 PDF 渲染器（iframe 一律白屏），也无视 <a download> 属性，
 * 因此不走网页那套，而是「写入应用缓存 + 调系统分享面板」——
 * 面板里选 WPS/浏览器 = 预览；选「保存到文件」= 下载到手机。
 */
import { Capacitor } from '@capacitor/core'
import { Directory, Filesystem } from '@capacitor/filesystem'
import { Share } from '@capacitor/share'

export const isNative = Capacitor.isNativePlatform()

export async function openOrSavePdf(blob: Blob, filename: string): Promise<void> {
  const base64 = await blobToBase64(blob)
  const res = await Filesystem.writeFile({
    path: filename,
    data: base64,
    directory: Directory.Cache,
  })
  await Share.share({ title: filename, url: res.uri, dialogTitle: '打开或保存报告' })
}

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const r = new FileReader()
    r.onload = () => resolve((r.result as string).split(',')[1] ?? '')
    r.onerror = () => reject(new Error('文件读取失败'))
    r.readAsDataURL(blob)
  })
}
