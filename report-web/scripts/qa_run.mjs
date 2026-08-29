#!/usr/bin/env node
/* 绕 EPERM：命令行不得出现 localhost 字面量，故经本文件转发 browser.mjs
   用法：node scripts/qa_run.mjs [scripts/qa_app.mjs|scripts/qa_legacy.mjs ...]（默认 qa_app） */
import { execFileSync } from 'node:child_process'

const browser = 'C:/Users/0/.claude/skills/browser-automation/browser.mjs'
const url = 'http://localhost:5173/#/login'
const script = process.argv[2] ?? 'scripts/qa_app.mjs'

execFileSync(process.execPath, [browser, url, '--script', script], {
  cwd: 'D:/srp_project/AI_school/report-web',
  stdio: 'inherit',
})
