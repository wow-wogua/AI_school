import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

/**
 * App 化后的路由分三形态（meta.layout）：
 *  - tab：底部 Tab 一级页（首页/班级/通知/我的），App.vue 挂底部 Tabbar
 *  - keepTab：从宫格进入的功能主页（成长记录流等），保留底部 Tabbar 但无激活态
 *  - sub：二级页，App.vue 挂「返回+标题」导航条（meta.title 为空则只有滚动容器，
 *    页面自带 hero，如学生详情）
 */
const router = createRouter({
  // hash 模式：安卓 WebView 无服务端回退路由，深链/重启必须靠 # 路径（Web 端同样可用）
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { layout: 'bare' } },
    // 底部 Tab 一级页
    { path: '/', component: () => import('../views/HomeView.vue'), meta: { layout: 'tab', tab: 'home' } },
    { path: '/class', component: () => import('../views/ClassView.vue'), meta: { layout: 'tab', tab: 'class' } },
    { path: '/notice', component: () => import('../views/NoticeView.vue'), meta: { layout: 'tab', tab: 'notice' } },
    { path: '/mine', component: () => import('../views/MineView.vue'), meta: { layout: 'tab', tab: 'mine' } },
    // 功能主页（保留 Tabbar）
    { path: '/feed', component: () => import('../views/FeedView.vue'), meta: { layout: 'keepTab' } },
    // 二级页（meta.title 驱动 App 壳顶部「返回+标题」导航条；学生详情自带 hero 故无 title）
    { path: '/student/:id', component: () => import('../views/StudentDetailView.vue'), meta: { layout: 'sub' } },
    { path: '/moment', component: () => import('../views/MomentListView.vue'), meta: { layout: 'sub', title: '微光瞬间' } },
    { path: '/moment/new', component: () => import('../views/MomentCaptureView.vue'), meta: { layout: 'sub', title: '微光信箱' } },
    // 功能页：挂 App 壳（内容精改按页推进）；admin 保留石实红（管理端体系）
    { path: '/reports', component: () => import('../views/ReportsView.vue'), meta: { layout: 'sub', title: '成长报告' } },
    { path: '/reports/:id/preview', component: () => import('../views/PreviewView.vue'), meta: { layout: 'sub', title: '报告预览' } },
    { path: '/comments', component: () => import('../views/CommentView.vue'), meta: { layout: 'sub', title: '班主任寄语' } },
    { path: '/activity', component: () => import('../views/ActivityView.vue'), meta: { layout: 'sub', title: '活动管理' } },
    { path: '/honor', component: () => import('../views/HonorView.vue'), meta: { layout: 'sub', title: '荣誉证书' } },
    { path: '/timeline', component: () => import('../views/TimelineView.vue'), meta: { layout: 'sub', title: '成长时间轴' } },
    { path: '/scores', component: () => import('../views/ScoreView.vue'), meta: { layout: 'sub', title: '成绩管理' } },
    { path: '/evaluate', component: () => import('../views/EvaluateView.vue'), meta: { layout: 'sub', title: '日常评价' } },
    { path: '/summary', component: () => import('../views/SummaryView.vue'), meta: { layout: 'sub', title: '成长总结' } },
    { path: '/comprehensive', component: () => import('../views/ComprehensiveView.vue'), meta: { layout: 'sub', title: '综合素质' } },
    { path: '/admin', component: () => import('../views/AdminView.vue'), meta: { layout: 'sub', title: '系统管理', admin: true } },
    // 原「批量任务」页（/）已并入通知页
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !auth.token) return '/login'
})

export default router
