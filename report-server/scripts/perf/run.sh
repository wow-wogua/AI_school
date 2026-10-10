#!/usr/bin/env bash
# 批44③ 压测封装：smoke / load / soak / report 四档 + docker stats 资源采样。
# 用法：bash run.sh smoke|load|soak|report
# 结果落 local/perf-results/<时间戳>_<档位>/（k6 summary + stats.log）
# 压测只跑本地栈（生产是正式数据，探活即可——README 红线）。
set -euo pipefail
cd "$(dirname "$0")"

BASE="${BASE:-http://localhost:8080}"
MODE="${1:-smoke}"
case "$MODE" in
  smoke)  SCRIPT=core-api.js;   VUS=2;  DUR=30s ;;
  load)   SCRIPT=core-api.js;   VUS=20; DUR=3m  ;;
  soak)   SCRIPT=core-api.js;   VUS=10; DUR=15m ;;
  report) SCRIPT=report-gen.js; VUS=2;  DUR=2m  ;;
  *) echo "用法：bash run.sh smoke|load|soak|report"; exit 1 ;;
esac

OUT="../../../local/perf-results/$(date +%Y%m%d_%H%M%S)_${MODE}"
mkdir -p "$OUT"

# k6 可执行：PATH 优先，其次 tools/ 下随仓库放的解压版（gitignored，31MB 不入库）
K6="${K6:-$(command -v k6 || true)}"
[ -z "$K6" ] && for c in ../../../tools/k6-v*-windows-amd64/k6.exe ../../../tools/k6/bin/k6; do
  [ -x "$c" ] && K6="$c" && break
done
[ -z "$K6" ] && { echo "未找到 k6（PATH 无且 tools/ 无解压版，安装见 README）"; exit 1; }

# 资源采样：每 5s 一行（时间 server-cpu/mem mysql-cpu/mem），跑完随结果归档
(
  while sleep 5; do
    line="$(docker stats --no-stream --format '{{.CPUPerc}} {{.MemUsage}}' aischool-server 2>/dev/null | tr -d '\n')"
    line="$line | $(docker stats --no-stream --format '{{.CPUPerc}} {{.MemUsage}}' aischool-mysql 2>/dev/null | tr -d '\n')"
    echo "$(date +%H:%M:%S) $line" >> "$OUT/stats.log"
  done
) &
STAT_PID=$!
trap 'kill $STAT_PID 2>/dev/null || true' EXIT

echo "== k6 $MODE：$SCRIPT VUS=$VUS DUR=$DUR BASE=$BASE =="
BASE="$BASE" VUS="$VUS" DUR="$DUR" "$K6" run "$SCRIPT" --summary-export "$OUT/summary.json" | tee "$OUT/console.log"

kill $STAT_PID 2>/dev/null || true
echo ""
echo "结果归档：$OUT（summary.json / console.log / stats.log）"
if [ "$MODE" = "report" ]; then
  echo "⚠ report 档会产生真实报告任务/报告行——验收后按 create_time 清理（见 README）"
fi
