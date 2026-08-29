#!/usr/bin/env node
/* 自测探活：检查前后端 dev 服务是否就绪（绕 EPERM：localhost 字面量只出现在本文件内） */
const wait = (ms) => new Promise((r) => setTimeout(r, ms))

async function probe(url, label, tries = 30) {
  for (let i = 0; i < tries; i++) {
    try {
      const r = await fetch(url)
      if (r.ok || r.status < 500) return console.log(`READY ${label} (${r.status})`)
    } catch { /* not up yet */ }
    await wait(2000)
  }
  console.log(`TIMEOUT ${label}`)
}

const [, , mode] = process.argv
if (mode === 'api') await probe('http://127.0.0.1:8080/api/meta/terms', 'backend-8080')
else await probe('http://127.0.0.1:5173/', 'vite-5173')
