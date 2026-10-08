<template>
  <!-- 批37 IA 重组：期末评语三合一（班主任寄语 / 成长总结 / 综合素质）——
       同一条期末写作链收拢为一个入口；旧路由 /comments /summary /comprehensive redirect 到此 -->
  <div class="page agg">
    <van-tabs v-model:active="active" class="agg-tabs" color="#A8232B" line-width="20px" title-active-color="#A8232B">
      <van-tab title="班主任寄语" name="comment" />
      <van-tab title="成长总结" name="summary" />
      <van-tab title="综合素质" name="comp" />
    </van-tabs>
    <KeepAlive>
      <CommentView v-if="active === 'comment'" />
      <SummaryView v-else-if="active === 'summary'" />
      <ComprehensiveView v-else />
    </KeepAlive>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import CommentView from './CommentView.vue'
import SummaryView from './SummaryView.vue'
import ComprehensiveView from './ComprehensiveView.vue'

const route = useRoute()
// 旧路由 redirect 带 ?tab= 直达对应段（如通知深链 /summary → 期末评语·成长总结）
const active = ref((route.query.tab as string) || 'comment')
</script>

<style scoped>
/* v-if 切段 + KeepAlive：已选班级/学生的上下文在段间保留，切回来不重选 */
.agg-tabs { margin-bottom: 4px; }
.agg :deep(.page) { padding: 0; }            /* 内嵌子页去掉自身页面级留白 */
.agg :deep(.page-title) { display: none; }    /* 子页内部大标题与 Tab 重复，隐藏 */
</style>
