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
    // 首登强制改密页（管理员设密/重置/批量导入初始密码后；改完才放行业务）
    { path: '/change-password', component: () => import('../views/ChangePasswordView.vue'), meta: { layout: 'bare' } },
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
    { path: '/profile', component: () => import('../views/ProfileView.vue'), meta: { layout: 'sub', title: '教师档案' } },
    { path: '/teacher-honor', component: () => import('../views/TeacherHonorView.vue'), meta: { layout: 'sub', title: '教师风采' } },
    { path: '/teacher-honor/new', component: () => import('../views/TeacherHonorCaptureView.vue'), meta: { layout: 'sub', title: '记录成就' } },
    { path: '/admin', component: () => import('../views/AdminView.vue'), meta: { layout: 'sub', title: '系统管理', admin: true } },
    // 家长端（PARENT 分流，方案C 新中式风格 --shine-*）：孩子卡+评价动态；报告批5 家长版再开
    { path: '/p/home', component: () => import('../views/parent/ParentHomeView.vue'), meta: { layout: 'ptab', tab: 'phome' } },
    { path: '/p/child/:id', component: () => import('../views/parent/ParentChildView.vue'), meta: { layout: 'psub', title: '孩子成长' } },
    { path: '/p/mine', component: () => import('../views/parent/ParentMineView.vue'), meta: { layout: 'ptab', tab: 'pmine' } },
    // 领导端（LEADER 分流）：全校只读驾驶舱；成绩明细复用 /scores（LEADER 只读）
    { path: '/l/home', component: () => import('../views/leader/LeaderHomeView.vue'), meta: { layout: 'lhome' } },
    // 原「批量任务」页（/）已并入通知页
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && to.path !== '/change-password' && !auth.token) return '/login'
  // 首登强制改密：未改密前一切页面都拦到改密页（改密页/登录页除外）
  if (auth.mustChangePwd && to.path !== '/change-password' && to.path !== '/login') {
    return '/change-password'
  }
  // 角色分流（批1）：PARENT 锁 /p/*；LEADER 锁 /l/*（+ /scores 成绩只读复用）；教师/管理员走现状路由
  const role = auth.role
  const free = to.path === '/login' || to.path === '/change-password'
  if (role === 'PARENT' && !free && !to.path.startsWith('/p/')) return '/p/home'
  if (role === 'LEADER' && !free && !to.path.startsWith('/l/') && !to.path.startsWith('/scores')) return '/l/home'
  if (role !== 'PARENT' && to.path.startsWith('/p/')) return '/'
  if (role !== 'LEADER' && to.path.startsWith('/l/')) return '/'
})

export default router
