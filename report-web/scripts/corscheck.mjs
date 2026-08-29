#!/usr/bin/env node
/* 后端 CORS 预检自测：模拟 App WebView 源（https://localhost）直连服务器 */
const api = 'http://127.0.0.1:8080/api/auth/login'
const origins = ['https://localhost', 'capacitor://localhost', 'http://localhost:5173', 'http://evil.example']

for (const origin of origins) {
  try {
    const r = await fetch(api, {
      method: 'OPTIONS',
      headers: { Origin: origin, 'Access-Control-Request-Method': 'POST', 'Access-Control-Request-Headers': 'authorization,content-type' },
    })
    const allow = r.headers.get('access-control-allow-origin')
    console.log(`${allow === origin ? 'PASS' : 'BLOCK'}  origin=${origin}  allow-origin=${allow}  status=${r.status}`)
  } catch (e) {
    console.log(`ERR   origin=${origin}  ${e.cause?.code ?? e.message}`)
  }
}
