/** App 页面通用时间格式化（动态流/任务列表用） */

function parse(v?: string | null): Date | null {
  if (!v) return null
  const d = new Date(v)
  return isNaN(d.getTime()) ? null : d
}

/** 图2/图3 卡片时间：今天显示 HH:mm，昨天显示「昨天」，再往前 MM-DD */
export function relTime(v?: string | null): string {
  const d = parse(v)
  if (!d) return ''
  const now = new Date()
  const sameDay = (a: Date, b: Date) => a.toDateString() === b.toDateString()
  if (sameDay(d, now)) return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (sameDay(d, yesterday)) return '昨天'
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 完整时间：YYYY-MM-DD HH:mm */
export function fullTime(v?: string | null): string {
  const d = parse(v)
  if (!d) return ''
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function pad(n: number) {
  return String(n).padStart(2, '0')
}
