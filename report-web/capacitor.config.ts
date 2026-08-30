import type { CapacitorConfig } from '@capacitor/cli'

/**
 * App 壳配置：webDir 指向 vite 构建产物；打包时可用环境变量注入默认服务器地址：
 *   cross-env VITE_API_BASE=http://ip:端口 npm run build   （Windows 本机打包建议装 cross-env）
 * App 内 API 地址运行时可在「登录页/我的页 · 服务器地址」修改（存 localStorage）。
 */
const config: CapacitorConfig = {
  appId: 'com.shishi.growth',
  appName: '数智成长',
  webDir: 'dist',
  android: {
    // 界面跑在 https://localhost（Capacitor 默认），而后端是 http://IP:端口（明文）——
    // 「https 页面发 http 请求」属混合内容，WebView 默认直接拦截（请求根本发不出，
    // 服务器日志一条不留）。usesCleartextTraffic 只开网络层的闸，这里再开内容策略的闸。
    // ⚠️ 键名必须是 allowMixedContent（读 @capacitor/android 的 CapConfig.java 源码确认：
    // 它找 "android.allowMixedContent" 布尔值；TS 类型声明里没这个键、写错键名会被静默忽略）。
    // 将来服务器上了 HTTPS（备案+域名）即可移除此配置。
    allowMixedContent: true,
  },
}

export default config
