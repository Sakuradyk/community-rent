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