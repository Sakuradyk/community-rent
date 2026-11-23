<template>
  <div class="layout">
    <header class="layout-header">
      <div class="layout-header-inner">
        <router-link to="/" class="brand">
          <span class="brand-mark" aria-hidden="true">邻</span>
          <span class="brand-text">邻里租</span>
        </router-link>

        <nav class="nav">
          <router-link to="/" class="nav-item">首页</router-link>
          <router-link :to="{ name: 'items' }" class="nav-item">逛物品</router-link>
          <router-link v-if="userStore.isLogin" :to="{ name: 'item-publish' }" class="nav-item">
            发布
          </router-link>
          <router-link v-if="userStore.isLogin" :to="{ name: 'item-mine' }" class="nav-item">
            我的物品
          </router-link>
          <router-link v-if="userStore.isLogin" :to="{ name: 'orders' }" class="nav-item">
            我的订单
          </router-link>
          <router-link v-if="userStore.isLogin" :to="{ name: 'credits' }" class="nav-item">
            我的信誉
          </router-link>
          <router-link to="/mine" class="nav-item">我的</router-link>
        </nav>

        <router-link
          v-if="userStore.isLogin"
          :to="{ name: 'messages' }"
          class="bell"
          aria-label="站内消息"
        >
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
            <span class="bell-icon">✉</span>
          </el-badge>
        </router-link>

        <div class="account">
          <template v-if="userStore.isLogin">
            <router-link :to="{ name: 'mine' }" class="account-name">
              {{ userStore.profile?.nickname || '已登录' }}
            </router-link>
            <el-button link @click="handleLogout">退出</el-button>
          </template>
          <template v-else>
            <el-button link @click="router.push({ name: 'login' })">登录</el-button>
            <el-button type="primary" @click="router.push({ name: 'register' })">注册</el-button>
          </template>
        </div>
      </div>
    </header>

    <main class="layout-main">
      <router-view />
    </main>

    <footer class="layout-footer">
      <div class="footer-inner muted">
        社区物品租用平台 · 本科毕业设计演示环境 · 模拟支付，不涉及真实资金
      </div>
    </footer>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getUnreadCount } from '@/api/message'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/** 顶栏未读徽标；只在有登录态时拉取，退出登录清零 */
const unreadCount = ref(0)

async function refreshUnread() {
  if (!userStore.isLogin) {
    unreadCount.value = 0
    return
  }
  try {
    const data = await getUnreadCount()
    unreadCount.value = data?.count ?? 0
  } catch (e) {
    // 未读数只是体验信息，失败静默（不打扰用户）
  }
}

// 每次路由切换都刷新一次：读完消息返回列表时徽标能立刻变化
watch(() => route.fullPath, refreshUnread)

onMounted(async () => {
  // 有令牌就用真实接口校准一次资料（防止改了资料后本地缓存过期）
  if (userStore.isLogin) {
    try {
      await userStore.fetchMe()
    } catch (e) {
      // 401 类错误已由 request.js 统一清理并跳转，这里不再重复提示
    }
  }
  await refreshUnread()
})

async function handleLogout() {
  await userStore.logout()
  unreadCount.value = 0
  ElMessage.success('已退出登录')
  router.push({ name: 'home' })
}
</script>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.layout {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.bell {
  display: inline-flex;
  align-items: center;
  margin-right: $space-3;
  color: $color-text-secondary;

  &:hover {
    color: $color-primary-deep;
  }
}

.bell-icon {
  font-size: 18px;
  line-height: 1;
}

.layout-header {
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid $color-border;
  position: sticky;
  top: 0;
  z-index: 10;
}

.layout-header-inner {
  max-width: $page-max-width;
  margin: 0 auto;
  height: $header-height;
  display: flex;
  align-items: center;
  gap: $space-8;
  padding: 0 $space-4;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: $space-2;
  color: $color-text;
}

.brand-mark {
  width: 30px;
  height: 30px;
  border-radius: $radius-sm;
  background: $color-primary;
  color: #fff;
  font-weight: $font-weight-bold;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(234, 88, 12, 0.24);
}

.brand-text {
  font-size: $font-size-md;
  font-weight: $font-weight-bold;
  letter-spacing: 0.5px;
}

.nav {
  display: flex;
  gap: $space-6;
  flex: 1;
}

// 导航：默认次要色，选中态用主色 + 底部下划线（比只变色更容易看清当前位置）
.nav-item {
  position: relative;
  height: $header-height;
  display: inline-flex;
  align-items: center;
  color: $color-text-secondary;
  font-size: $font-size-sm;

  &::after {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 2px;
    background: transparent;
    border-radius: 2px 2px 0 0;
  }

  &.router-link-exact-active {
    color: $color-primary-deep;
    font-weight: $font-weight-medium;

    &::after { background: $color-primary; }
  }
}

.account {
  display: flex;
  align-items: center;
  gap: $space-3;
}

.account-name {
  font-size: $font-size-sm;
  color: $color-text;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.layout-main { flex: 1; }

.layout-footer {
  border-top: 1px solid $color-border;
  background: $color-surface;
}

.footer-inner {
  max-width: $page-max-width;
  margin: 0 auto;
  padding: $space-6 $space-4;
  text-align: center;
}
</style>
