#!/bin/bash
# M7 全量回归链（顺序执行，单步失败不阻断，汇总 RESULT 行）
# 用法：bash scripts/run_regression.sh（需后端 8080 + vite 5173 + docker 基础设施已起）
# 本机指定 Python：PY=<python路径> bash scripts/run_regression.sh
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PY="${PY:-python}"
overall=0

run() { # $1=标记 $2...=命令
  local tag=$1; shift
  echo "===== [${tag}] start $(date +%H:%M:%S) ====="
  "$@" 2>&1 | tail -3
  local rc=${PIPESTATUS[0]}
  if [ $rc -ne 0 ]; then overall=1; echo "===== [${tag}] EXIT=$rc FAIL ====="; else echo "===== [${tag}] OK ====="; fi
}

cd "$ROOT/report-server" || exit 1
run contract   $PY -X utf8 scripts/verify_contract.py
run rbac       $PY -X utf8 scripts/verify_rbac.py
run ai         $PY -X utf8 scripts/verify_ai.py
run retry      $PY -X utf8 scripts/verify_retry.py
run concurrency $PY -X utf8 scripts/verify_concurrency.py

cd "$ROOT/report-web" || exit 1
run web        node e2e/verify_web.mjs

cd "$ROOT/report-server" || exit 1
run m6         $PY -X utf8 scripts/verify_m6.py

cd "$ROOT/report-web" || exit 1
run m6_web     node e2e/verify_m6_web.mjs
run m7_web     node e2e/verify_m7_web.mjs

cd "$ROOT/report-server" || exit 1
run m7         $PY -X utf8 scripts/verify_m7.py

echo "===== REGRESSION OVERALL: $([ $overall -eq 0 ] && echo PASS || echo FAIL) ====="
exit $overall
