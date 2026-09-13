import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const pkg = JSON.parse(readFileSync(fileURLToPath(new URL('./package.json', import.meta.url)), 'utf-8'))

/** 构建时生成 dist/version.json：App 启动据此识别「服务器前端已更新」（utils/appUpdate.ts 资源轻更） */
function versionJson() {
  return {
    name: 'version-json',
    generateBundle() {
      this.emitFile({
        type: 'asset',
        fileName: 'version.json',
        source: JSON.stringify({ builtAt: new Date().toISOString() }),
      })
    },
  }
}

export default defineConfig({
  plugins: [vue(), versionJson()],
  define: { __APP_VERSION__: JSON.stringify(pkg.version) },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
})
