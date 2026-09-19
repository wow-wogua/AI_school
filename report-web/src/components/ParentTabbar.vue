<template>
  <!-- 家长端底部导航（批1）：两 Tab + 中央无「记录」按钮（家长只读参与，不产生记录） -->
  <nav class="ptabbar" aria-label="家长端导航">
    <RouterLink to="/p/home" class="tab" :class="{ on: tab === 'phome' }">
      <van-icon :name="tab === 'phome' ? 'wap-home' : 'wap-home-o'" /><span>首页</span>
    </RouterLink>
    <RouterLink to="/p/mine" class="tab" :class="{ on: tab === 'pmine' }">
      <van-icon :name="tab === 'pmine' ? 'manager' : 'manager-o'" /><span>我的</span>
    </RouterLink>
  </nav>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'

defineProps<{ tab: 'phome' | 'pmine' }>()
</script>

<style scoped>
.ptabbar { position: fixed; left: 0; right: 0; bottom: 0; z-index: 200; display: flex; align-items: stretch;
  background: rgba(255, 255, 255, .97); backdrop-filter: blur(12px);
  border-top: 1px solid var(--shine-line); padding-bottom: var(--sab); }
/* 平板限宽居中（与 .app-page 同步 720px） */
@media (min-width: 600px) {
  .ptabbar { left: 50%; transform: translateX(-50%); width: 720px; border-radius: 18px 18px 0 0; }
}
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px;
  padding: 8px 0 6px; text-decoration: none; color: var(--app-text-3); font-size: 10px;
  background: none; border: none; -webkit-tap-highlight-color: transparent; }
.tab .van-icon { font-size: 22px; }
/* 激活态=C 品牌红（方案C：操作/品牌色） */
.tab.on { color: var(--shine-red); font-weight: 600; }
.tab span { line-height: 1; }
</style>
