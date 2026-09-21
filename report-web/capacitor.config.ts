import type { CapacitorConfig } from '@capacitor/cli'

/**
 * App 壳配置：webDir 指向 vite 构建产物；打包时可用环境变量注入默认服务器地址：
 *   cross-env VITE_API_BASE=http://ip:端口 npm run build   （Windows 本机打包建议装 cross-env）
 * App 内 API 地址运行时可在「登录页/我的页 · 服务器地址」修改（存 localStorage）。
 *
 * 在线升级模式：打包脚本（local/build_apk.sh）同时注入 CAP_SERVER_URL 时，WebView 直接
 * 加载服务器上的前端页面——前端改动=服务器重建 web 容器即对 App 全量生效，老师无需重装 APK
 * （v1.0.14 起启用）。不注入（本地 dev 联调）则保持本地资源模式。
 */
const serverUrl = process.env.CAP_SERVER_URL

const config: CapacitorConfig = {
  appId: 'com.shishi.growth',
  appName: '石实SHINE',
  webDir: 'dist',
  // WebView 底色 = 品牌深蓝：加载服务器页面的空档/键盘弹出时的露底不再闪白
  backgroundColor: '#1F2A44',
  // 页面与 API 同源（http://服务器），比「本地 https 壳发 http 请求」更干净，
  // CORS/混合内容问题一并消失；usesCleartextTraffic 明文闸此前已开。
  ...(serverUrl ? { server: { url: serverUrl } } : {}),
  android: {
    // 界面跑在 https://localhost（Capacitor 默认），而后端是 http://IP:端口（明文）——
    // 「https 页面发 http 请求」属混合内容，WebView 默认直接拦截（请求根本发不出，
    // 服务器日志一条不留）。usesCleartextTraffic 只开网络层的闸，这里再开内容策略的闸。
    // ⚠️ 键名必须是 allowMixedContent（读 @capacitor/android 的 CapConfig.java 源码确认：
    // 它找 "android.allowMixedContent" 布尔值；TS 类型声明里没这个键、写错键名会被静默忽略）。
    // 将来服务器上了 HTTPS（备案+域名）即可移除此配置。
    allowMixedContent: true,
  },
  plugins: {
    // Android 15+ 强制 edge-to-edge：内容延伸到状态栏/手势条底下（深蓝头区正好做沉浸式）。
    // insetsHandling 默认 'css' 即注入 --safe-area-inset-* 变量（style.css :root 已接）；
    // style DARK = 深色背景配浅色图标——状态栏白字压在深蓝头区上（App 各页顶部均为深蓝）
    SystemBars: { style: 'DARK' },
  },
}

export default config
