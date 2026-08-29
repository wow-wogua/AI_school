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
}

export default config
