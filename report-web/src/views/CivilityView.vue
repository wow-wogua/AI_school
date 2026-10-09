<template>
  <div class="app-page civility">
    <div class="app-sec">文明班评比</div>

    <!-- 批39⑥ 数据源并入素养评价；批43 再并入班级整体加减分 -->
    <div class="app-card tex-e basis">
      <p class="basis-txt">班级总分自动汇总：检查日基础 120 分 + 老师素养评价分直加（1 评价分 = 1 文明班分）+ 班级整体加减分（卫生检查、全班获奖等，由班主任/级长在「素养评价 → 班级记分」录入）。给学生记日常评价即可，无需另行打分。</p>
    </div>

    <!-- 自动汇总：本月排名（年级分组，各卡前 5 名） -->
    <div class="app-sec">本月排名（自动汇总）</div>
    <div v-for="g in rankGrades" :key="g.gradeId" class="app-card rank-card">
      <p class="g-head">{{ g.gradeName || '年级' }}</p>
      <div v-for="c in g.classes.slice(0, 5)" :key="c.classId" class="rk" :class="'top-' + c.rankNo">
        <span class="no">{{ c.rankNo }}</span>
        <b>{{ c.className }}</b>
        <span class="sc">{{ c.totalScore }}</span>
      </div>
    </div>

    <div class="app-sec">打分记录（历史）</div>
    <div class="app-card list">
      <div v-if="!records.length" class="empty">暂无打分记录</div>
      <div v-for="r in records" :key="r.id" class="row">
        <div class="r-body">
          <p class="r-title">
            <van-tag plain :type="Number(r.delta) >= 0 ? 'warning' : 'danger'">
              {{ Number(r.delta) >= 0 ? '+' : '' }}{{ r.delta }}×{{ r.cnt }}
            </van-tag>
            <b>{{ r.className }}</b> · {{ r.itemText }}
          </p>
          <p class="r-sub">{{ r.scoreDate }} · {{ r.operatorName }}<template v-if="r.note"> · {{ r.note }}</template></p>
        </div>
      </div>
    </div>

    <p class="app-foot">石实实验学校 · 石实SHINE</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api/http'

const records = ref<any[]>([])
const rankGrades = ref<any[]>([])

const today = new Date()
const fmt = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

async function loadRecords() {
  // 批39⑥ 打分入口下线：近 200 条历史留档（不再按今日筛选）
  records.value = await api<any[]>('/api/civility/records').catch(() => [])
}

async function loadRank() {
  const from = fmt(new Date(today.getFullYear(), today.getMonth(), 1))
  const d = await api<any>(`/api/civility/rank?from=${from}&to=${fmt(today)}`).catch(() => null)
  rankGrades.value = d?.grades ?? []
}

onMounted(async () => {
  await Promise.all([loadRecords(), loadRank()])
})
</script>

<style scoped>
.basis { padding: 12px 16px; margin-top: 12px; }
.basis-txt { margin: 0; font-size: 12px; line-height: 1.7; color: var(--app-text-2); }

.rank-card { padding: 12px 14px; margin-top: 12px; }
.g-head { margin: 0 0 6px; font-size: 14px; font-weight: 700; color: var(--app-text-1); }
.rk { display: flex; align-items: center; gap: 10px; padding: 7px 2px; font-size: 13px; }
.no { flex: none; width: 22px; height: 22px; border-radius: 50%; display: flex; align-items: center;
  justify-content: center; font-size: 12px; font-weight: 700; background: var(--app-card-border); color: var(--app-text-2); }
.rk.top-1 .no { background: #EAB308; color: #fff; }
.rk.top-2 .no { background: #94A3B8; color: #fff; }
.rk.top-3 .no { background: #D97706; color: #fff; }
.rk b { flex: 1; color: var(--app-text-1); }
.sc { font-weight: 700; color: var(--app-text-2); }

.list { margin-top: 12px; padding: 6px 14px; }
.empty { padding: 26px 0; text-align: center; font-size: 13px; color: var(--app-text-3); }
.row { padding: 12px 0; }
.row + .row { border-top: 1px solid var(--app-card-border); }
.r-title { margin: 0; font-size: 13px; color: var(--app-text-1); line-height: 1.6; }
.r-title b { font-size: 14px; }
.r-sub { margin: 4px 0 0; font-size: 11px; color: var(--app-text-3); }
</style>
