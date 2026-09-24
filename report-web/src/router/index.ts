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
    // 家长自助注册（批8.6）：学号+邀请码绑定，免登录
    { path: '/p/register', component: () => import('../views/ParentRegisterView.vue'), meta: { layout: 'bare' } },
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
    { path: '/bank', component: () => import('../views/TeacherBankView.vue'), meta: { layout: 'sub', title: '成长银行' } },
    { path: '/conduct-rules', component: () => import('../views/ConductRulesView.vue'), meta: { layout: 'sub', title: '德育规范' } },
    // 行政办公（批9）：公章三级审批+物资申领（OA 引擎）；批10 加教师请假
    { path: '/oa', component: () => import('../views/OaView.vue'), meta: { layout: 'sub', title: '行政办公' } },
    // 报修（批10）：文字+拍照凭证，工单直达管理端
    { path: '/repair', component: () => import('../views/RepairView.vue'), meta: { layout: 'sub', title: '报修' } },
    // 谈心记录（批11）：教师对可见班级学生，管理端全量
    { path: '/talk', component: () => import('../views/TalkView.vue'), meta: { layout: 'sub', title: '谈心记录' } },
    // 意见反馈（批8.5）：教师/家长共用组件，双路由分流（家长被锁 /p/*）
    { path: '/feedback', component: () => import('../views/FeedbackView.vue'), meta: { layout: 'sub', title: '意见反馈' } },
    { path: '/profile', component: () => import('../views/ProfileView.vue'), meta: { layout: 'sub', title: '教师档案' } },
    { path: '/teacher-honor', component: () => import('../views/TeacherHonorView.vue'), meta: { layout: 'sub', title: '教师风采' } },
    { path: '/teacher-honor/new', component: () => import('../views/TeacherHonorCaptureView.vue'), meta: { layout: 'sub', title: '记录成就' } },
    { path: '/admin', component: () => import('../views/AdminView.vue'), meta: { layout: 'sub', title: '系统管理', admin: true } },
    // 家长端（PARENT 分流，方案C 新中式风格 --shine-*）：孩子卡+评价动态；报告批5 家长版再开
    { path: '/p/home', component: () => import('../views/parent/ParentHomeView.vue'), meta: { layout: 'ptab', tab: 'phome' } },
    { path: '/p/child/:id', component: () => import('../views/parent/ParentChildView.vue'), meta: { layout: 'psub', title: '孩子成长' } },
    { path: '/p/mine', component: () => import('../views/parent/ParentMineView.vue'), meta: { layout: 'ptab', tab: 'pmine' } },
    // 内容（批2）：通知公告/育儿课堂共用列表页（props 区分），详情按 id
    { path: '/p/notices', component: () => import('../views/parent/ContentListView.vue'), props: { type: 'NOTICE' }, meta: { layout: 'psub', title: '通知公告' } },
    { path: '/p/parenting', component: () => import('../views/parent/ContentListView.vue'), props: { type: 'PARENTING' }, meta: { layout: 'psub', title: '育儿课堂' } },
    { path: '/p/content/:id', component: () => import('../views/parent/ContentDetailView.vue'), meta: { layout: 'psub', title: '内容详情' } },
    // 家长微光信箱（批2-3 方案A）：拍照仅进孩子成长档案（家长+班主任可见）
    { path: '/p/moments', component: () => import('../views/parent/ParentMomentView.vue'), meta: { layout: 'psub', title: '微光信箱' } },
    // 成长报告（批5 家长版）：同任务双渲染的去成绩版 PDF，仅 parent_file_url 可达
    { path: '/p/report', component: () => import('../views/parent/ParentReportView.vue'), meta: { layout: 'psub', title: '成长报告' } },
    // 意见反馈（批8.5）：家长入口
    { path: '/p/feedback', component: () => import('../views/FeedbackView.vue'), meta: { layout: 'psub', title: '意见反馈' } },
    // 领导端（LEADER 分流）：全校只读驾驶舱；成绩明细复用 /scores（LEADER 只读）；
    // 教师使用情况（批2）：六类行为按师聚合，psub 壳（第一版 sub-nav 形态+C 令牌）
    { path: '/l/home', component: () => import('../views/leader/LeaderHomeView.vue'), meta: { layout: 'lhome' } },
    { path: '/l/teachers', component: () => import('../views/leader/LeaderTeachersView.vue'), meta: { layout: 'psub', title: '教师使用情况' } },
    // 全校成绩汇总（批4）：总分/单科排名+各班统计+导出（方案A 领导全可见）
    { path: '/l/scores', component: () => import('../views/leader/LeaderScoreSummaryView.vue'), meta: { layout: 'psub', title: '成绩汇总排名' } },
    // 账号审批（批2-5）：管理员/领导账号双人审批，领导 App 端入口
    { path: '/l/approvals', component: () => import('../views/leader/LeaderApprovalsView.vue'), meta: { layout: 'psub', title: '待我审批' } },
    // 原「批量任务」页（/）已并入通知页
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && to.path !== '/change-password' && to.path !== '/p/register' && !auth.token) return '/login'
  // 首登强制改密：未改密前一切页面都拦到改密页（改密页/登录页除外）
  if (auth.mustChangePwd && to.path !== '/change-password' && to.path !== '/login') {
    return '/change-password'
  }
  // 角色分流：PARENT 锁 /p/*；教师/管理员/领导走现状路由——批3.5 领导教师化：
  // 领导不再锁 /l/*，教师功能全量可用（首页宫格进领导驾驶舱），/l/* 仅领导可进
  const role = auth.role
  // /p/register 免登开放（未登录 role 为空，不能被 /p/* 锁拦回）
  const free = to.path === '/login' || to.path === '/change-password' || to.path === '/p/register'
  if (role === 'PARENT' && !free && !to.path.startsWith('/p/')) return '/p/home'
  if (role !== 'PARENT' && !free && to.path.startsWith('/p/')) return '/'
  if (role !== 'LEADER' && to.path.startsWith('/l/')) return '/'
})

export default router
