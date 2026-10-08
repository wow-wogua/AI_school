<template>
  <!-- 批37 IA 重组：我的成长三合一（教师档案 / 教师风采 / 成长足迹）——
       教师自身成长相关收拢为一个入口；旧路由 /profile /teacher-honor /footprint redirect 到此 -->
  <div class="page agg">
    <van-tabs v-model:active="active" class="agg-tabs" color="#A8232B" line-width="20px" title-active-color="#A8232B">
      <van-tab title="教师档案" name="profile" />
      <van-tab title="教师风采" name="honor" />
      <van-tab title="成长足迹" name="footprint" />
    </van-tabs>
    <KeepAlive>
      <!-- 管理员档案=全校总览页签（在管理端），这里给跳转入口 -->
      <div v-if="active === 'profile' && auth.role === 'ADMIN'" class="admin-entry">
        <p>教师档案全校总览在管理端</p>
        <el-button type="primary" @click="$router.push('/admin?tab=teacherProfile')">前往管理端查看</el-button>
      </div>
      <ProfileView v-else-if="active === 'profile'" />
      <TeacherHonorView v-else-if="active === 'honor'" />
      <FootprintView v-else />
    </KeepAlive>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import ProfileView from './ProfileView.vue'
import TeacherHonorView from './TeacherHonorView.vue'
import FootprintView from './FootprintView.vue'

const auth = useAuthStore()
const route = useRoute()
// 旧路由 redirect 带 ?tab= 直达对应段
const active = ref((route.query.tab as string) || 'profile')
</script>

<style scoped>
.agg-tabs { margin-bottom: 4px; }
.agg :deep(.page) { padding: 0; }
.agg :deep(.page-title) { display: none; }
.admin-entry { padding: 40px 20px; text-align: center; color: var(--app-text-3); }
.admin-entry p { margin: 0 0 14px; font-size: 14px; }
</style>
