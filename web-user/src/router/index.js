import { createRouter, createWebHistory } from 'vue-router'
import DefaultLayout from '@/layout/DefaultLayout.vue'
import { setupRouterGuard } from './guard'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { public: true, title: '注册' }
  },
  {
    path: '/forgot',
    name: 'forgot',
    component: () => import('@/views/auth/ForgotView.vue'),
    meta: { public: true, title: '找回密码' }
  },
  {
    path: '/',
    component: DefaultLayout,
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/home/HomeView.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'items',
        name: 'items',
        component: () => import('@/views/item/ItemListView.vue'),
        meta: { title: '逛物品' }
      },
      // 静态路径必须排在 '/items/:id' 之前，否则 publish/mine 会被当成物品 id
      {
        path: 'items/publish',
        name: 'item-publish',
        component: () => import('@/views/item/ItemPublishView.vue'),
        meta: { title: '发布物品', requiresAuth: true }
      },
      {
        path: 'items/mine',
        name: 'item-mine',
        component: () => import('@/views/item/ItemMineView.vue'),
        meta: { title: '我的物品', requiresAuth: true }
      },
      {
        path: 'items/:id',
        name: 'item-detail',
        component: () => import('@/views/item/ItemDetailView.vue'),
        meta: { title: '物品详情' }
      },
      {
        path: 'orders',
        name: 'orders',
        component: () => import('@/views/order/OrderListView.vue'),
        meta: { title: '我的订单', requiresAuth: true }
      },
      // 同样：静态段 'orders/create' 必须排在 'orders/:id' 之前
      {
        path: 'orders/create',
        name: 'order-create',
        component: () => import('@/views/order/OrderCreateView.vue'),
        meta: { title: '填写租用信息', requiresAuth: true }
      },
      {
        path: 'orders/:id',
        name: 'order-detail',
        component: () => import('@/views/order/OrderDetailView.vue'),
        meta: { title: '订单详情', requiresAuth: true }
      },
      {
        path: 'settlements/:orderId',
        name: 'settlement',
        component: () => import('@/views/settlement/SettlementView.vue'),
        meta: { title: '结算单', requiresAuth: true }
      },
      {
        path: 'credits',
        name: 'credits',
        component: () => import('@/views/credit/CreditView.vue'),
        meta: { title: '我的信誉', requiresAuth: true }
      },      {
        path: 'messages',
        name: 'messages',
        component: () => import('@/views/message/MessageView.vue'),
        meta: { title: '站内消息', requiresAuth: true }
      },
      {
        path: 'wallet',
        name: 'wallet',
        component: () => import('@/views/wallet/WalletView.vue'),
        meta: { title: '我的账单', requiresAuth: true }
      },
      {
        path: 'mine',
        name: 'mine',
        component: () => import('@/views/mine/ProfileView.vue'),
        meta: { title: '我的', requiresAuth: true }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

setupRouterGuard(router)

export default router