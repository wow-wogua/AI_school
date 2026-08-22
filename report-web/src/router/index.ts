import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('../views/LoginView.vue') },
    { path: '/', component: () => import('../views/TasksView.vue') },
    { path: '/reports', component: () => import('../views/ReportsView.vue') },
    { path: '/reports/:id/preview', component: () => import('../views/PreviewView.vue') },
    { path: '/comments', component: () => import('../views/CommentView.vue') },
    { path: '/activity', component: () => import('../views/ActivityView.vue') },
    { path: '/honor', component: () => import('../views/HonorView.vue') },
    { path: '/timeline', component: () => import('../views/TimelineView.vue') },
    { path: '/scores', component: () => import('../views/ScoreView.vue') },
    { path: '/evaluate', component: () => import('../views/EvaluateView.vue') },
    { path: '/summary', component: () => import('../views/SummaryView.vue') },
    { path: '/comprehensive', component: () => import('../views/ComprehensiveView.vue') },
    { path: '/admin', component: () => import('../views/AdminView.vue') },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !auth.token) return '/login'
})

export default router
