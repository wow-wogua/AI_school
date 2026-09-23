<template>
  <div>
    <div class="hd">
      <div class="sum">
        <span class="ok" v-if="!dangerCount && !warnCount">✓ 数据全部健康</span>
        <span v-if="dangerCount" class="n danger"><b>{{ dangerCount }}</b> 项高危</span>
        <span v-if="warnCount" class="n warn"><b>{{ warnCount }}</b> 项待关注</span>
        <el-button size="small" :loading="loading" @click="load">重新扫描</el-button>
      </div>
      <div class="tip">扫描缺手机号 / 无班无课 / 学号异常 / 班内重复 / 宿舍不全 / 无班主任 / 家长未绑定；点问题行直达对应页签处理。</div>
    </div>

    <div v-for="g in groups" :key="g.key" class="grp" :class="g.level">
      <div class="grp-hd" @click="toggle(g.key)">
        <el-icon class="ic"><component :is="g.level === 'danger' ? WarningFilled : InfoFilled" /></el-icon>
        <b>{{ g.title }}</b>
        <el-tag :type="g.count ? (g.level === 'danger' ? 'danger' : 'warning') : 'success'" size="small">
          {{ g.count ? g.count + ' 条' : '正常' }}
        </el-tag>
        <el-icon class="arrow" :class="{ open: opened.has(g.key) }"><ArrowDown /></el-icon>
      </div>
      <template v-if="opened.has(g.key) && g.count">
        <div class="note">{{ g.note }}</div>
        <el-table :data="g.items" size="small" @row-click="(r: any) => goFix(g, r)">
          <el-table-column prop="label" label="对象" min-width="160" />
          <el-table-column prop="sub" label="详情" min-width="200" show-overflow-tooltip />
          <el-table-column v-if="g.items.some((i: any) => i.kw)" label="" width="90">
            <template #default><span class="fix">去处理 →</span></template>
          </el-table-column>
        </el-table>
        <div v-if="g.count > g.items.length" class="more">仅展示前 {{ g.items.length }} 条，共 {{ g.count }} 条</div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown, WarningFilled, InfoFilled } from '@element-plus/icons-vue'
import { api } from '../../api/http'

const emit = defineEmits<{ (e: 'scanned', danger: number, warn: number): void }>()

const router = useRouter()
const groups = ref<any[]>([])
const loading = ref(false)
const opened = ref(new Set<string>())

const dangerCount = computed(() => groups.value.filter((g) => g.level === 'danger' && g.count).length)
const warnCount = computed(() => groups.value.filter((g) => g.level === 'warning' && g.count).length)

async function load() {
  loading.value = true
  try {
    groups.value = await api<any[]>('/api/admin/health/scan')
    // 有问题的组自动展开（首轮），正常的收起
    for (const g of groups.value) {
      if (g.count) opened.value.add(g.key)
    }
    opened.value = new Set(opened.value)
    emit('scanned', dangerCount.value, warnCount.value)
  } finally {
    loading.value = false
  }
}

function toggle(key: string) {
  const n = new Set(opened.value)
  n.has(key) ? n.delete(key) : n.add(key)
  opened.value = n
}

/** 直达修复：跳对应页签并带搜索词（TeacherTab/StudentTab 读 ?kw= 预填） */
function goFix(g: any, row: any) {
  if (!row.kw) {
    if (g.target) router.push({ path: '/admin', query: { tab: g.target } })
    return
  }
  router.push({ path: '/admin', query: { tab: g.target, kw: row.kw } })
}

onMounted(load)
</script>

<style scoped>
.hd { margin-bottom: 14px; }
.sum { display: flex; align-items: center; gap: 12px; }
.sum .ok { color: var(--el-color-success); font-weight: 600; }
.n { font-size: 13px; color: var(--el-text-color-secondary); }
.n b { font-size: 18px; margin-right: 2px; }
.n.danger b { color: var(--el-color-danger); }
.n.warn b { color: var(--el-color-warning); }
.tip { margin-top: 8px; font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.6; }

.grp { border: 1px solid var(--el-border-color-lighter); border-radius: 10px; margin-bottom: 10px; overflow: hidden; }
.grp-hd { display: flex; align-items: center; gap: 8px; padding: 10px 14px; cursor: pointer; background: #FAFBFD; }
.grp-hd b { font-size: 13.5px; }
.grp-hd .ic { color: var(--el-color-info); }
.grp.danger .grp-hd .ic { color: var(--el-color-danger); }
.grp-hd .arrow { margin-left: auto; color: var(--el-text-color-placeholder); transition: transform .2s; }
.grp-hd .arrow.open { transform: rotate(180deg); }
.note { padding: 8px 14px; font-size: 12px; color: var(--el-text-color-secondary); background: #FAFBFD;
  border-bottom: 1px dashed var(--el-border-color-lighter); }
.grp :deep(.el-table) { cursor: pointer; }
.fix { color: var(--el-color-primary); font-size: 12px; }
.more { padding: 6px 14px 8px; font-size: 12px; color: var(--el-text-color-placeholder); }
</style>
