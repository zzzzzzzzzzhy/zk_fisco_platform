import Vue from 'vue'
import VueRouter from 'vue-router'
import store from '@/store'

Vue.use(VueRouter)

const routes = [
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/views/Layout.vue'),
    redirect: '/competitions',
    children: [
      {
        path: '/competitions',
        name: 'Competitions',
        component: () => import(/* webpackChunkName: "competitions" */ '@/views/Competitions.vue'),
        meta: { title: '竞赛列表' }
      },
      {
        path: '/forum',
        name: 'Forum',
        component: () => import(/* webpackChunkName: "forum" */ '@/views/Forum.vue'),
        meta: { title: '社区论坛' }
      },
      {
        path: '/content-share',
        name: 'ContentShare',
        component: () => import(/* webpackChunkName: "content-share" */ '@/views/ContentShare.vue'),
        meta: { title: '内容分享' }
      },
      {
        path: '/coffee-mapping',
        name: 'CoffeeMapping',
        component: () => import(/* webpackChunkName: "coffee" */ '@/views/CoffeeMapping.vue'),
        meta: { title: '咖啡映射' }
      },
      {
        path: '/competitions/:id',
        name: 'CompetitionDetail',
        component: () => import('@/views/CompetitionDetail.vue'),
        meta: { title: '竞赛详情' }
      },
      {
        path: '/profile',
        name: 'Profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人中心', requiresAuth: true }
      },
      {
        path: '/submissions',
        name: 'Submissions',
        component: () => import('@/views/Submissions.vue'),
        meta: { title: '我的提交', requiresAuth: true }
      },
      // 排行榜（公开页面）
      {
        path: '/leaderboards/:id',
        name: 'LeaderboardDetail',
        component: () => import('@/views/LeaderboardDetail.vue'),
        meta: { title: '排行榜详情' }
      },
      // DAO 治理
      {
        path: '/governance',
        name: 'Governance',
        component: () => import(/* webpackChunkName: "governance" */ '@/views/Governance/ProposalList.vue'),
        meta: { title: 'DAO 治理' }
      },
      {
        path: '/governance/proposals/:id',
        name: 'ProposalDetail',
        component: () => import(/* webpackChunkName: "governance" */ '@/views/Governance/ProposalDetail.vue'),
        meta: { title: '提案详情' }
      }
    ]
  },
  {
    path: '/content-share/:id',
    name: 'ContentShareDetail',
    component: () => import('@/views/ContentShareDetail.vue'),
    meta: { title: '内容详情' }
  },
  // 管理后台
  {
    path: '/admin',
    component: () => import(/* webpackChunkName: "admin" */ '@/views/AdminLayout.vue'),
    meta: { requiresAuth: true },
    redirect: '/admin/competitions',
    children: [
      {
        path: 'competitions',
        name: 'AdminCompetitionsPage',
        component: () => import(/* webpackChunkName: "admin" */ '@/views/AdminCompetitions.vue'),
        meta: { title: '竞赛管理', requiresAuth: true }
      },
      {
        path: 'leaderboards',
        name: 'AdminLeaderboards',
        component: () => import('@/views/Leaderboards.vue'),
        meta: { title: '排行榜管理', requiresAuth: true }
      },
      {
        path: 'gas-monitoring',
        name: 'AdminGasMonitoring',
        component: () => import('@/views/GasMonitoring.vue'),
        meta: { title: 'Gas费监控', requiresAuth: true }
      },
      {
        path: 'content-reports',
        name: 'AdminContentReports',
        component: () => import('@/views/AdminContentReports.vue'),
        meta: { title: '内容举报管理', requiresAuth: true }
      },
      {
        path: 'content-shares',
        name: 'AdminContentShares',
        component: () => import('@/views/AdminContentShares.vue'),
        meta: { title: '内容分享管理', requiresAuth: true }
      },
      {
        path: 'appeals',
        name: 'AdminAppeals',
        component: () => import('@/views/LeaderboardAppeals.vue'),
        meta: { title: '异议管理', requiresAuth: true }
      },
      {
        path: 'prizes',
        name: 'AdminPrizes',
        component: () => import('@/views/PrizeManagement.vue'),
        meta: { title: '奖金管理', requiresAuth: true }
      },
      {
        path: 'competitions/create',
        name: 'CreateCompetition',
        component: () => import('@/views/CompetitionForm.vue'),
        meta: { title: '创建竞赛', requiresAuth: true }
      },
      {
        path: 'competitions/edit/:id',
        name: 'EditCompetition',
        component: () => import('@/views/CompetitionForm.vue'),
        meta: { title: '编辑竞赛', requiresAuth: true }
      }
    ]
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册' }
  }
]

const router = new VueRouter({
  mode: 'history',
  base: process.env.BASE_URL,
  routes
})

// 解决 NavigationDuplicated 报错
const originalPush = VueRouter.prototype.push
VueRouter.prototype.push = function push(location) {
  return originalPush.call(this, location).catch(err => {
    if (err.name !== 'NavigationDuplicated') throw err
  })
}

// 路由守卫
router.beforeEach((to, _from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 竞赛平台` : '竞赛平台'

  const token = store.state.user.token

  if (to.meta.requiresAuth && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

export default router
