<template>
  <div class="layout">
    <header class="layout-header">
      <div class="layout-header-inner">
        <router-link to="/" class="brand">邻里租</router-link>
        <nav class="nav">
          <router-link to="/" class="nav-item">首页</router-link>
          <router-link to="/mine" class="nav-item">我的</router-link>
        </nav>
        <div class="account">
          <template v-if="userStore.isLogin">
            <span class="nickname">{{ userStore.profile?.nickname || '已登录' }}</span>
            <el-button link type="primary" @click="handleLogout">退出</el-button>
          </template>
          <template v-else>
            <el-button link type="primary" @click="router.push({ name: 'login' })">登录</el-button>
            <el-button type="primary" size="small" @click="router.push({ name: 'register' })">注册</el-button>
          </template>
        </div>
      </div>
    </header>

    <main class="layout-main">
      <router-view />
    </main>

    <footer class="layout-footer muted">
      社区物品租用平台 · 本科毕业设计演示环境
    </footer>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

onMounted(async () => {
  // 有令牌就用真实接口校准一次资料（防止改了资料后本地缓存过期）
  if (userStore.isLogin) {
    try {
      await userStore.fetchMe()
    } catch (e) {
      // 401 类错误已由 request.js 统一清理并跳转，这里不再重复提示
    }
  }
})

async function handleLogout() {
  await userStore.logout()
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

.layout-header {
  background: #fff;
  border-bottom: 1px solid $color-border;
  position: sticky;
  top: 0;
  z-index: 10;
}

.layout-header-inner {
  max-width: $page-max-width;
  margin: 0 auto;
  height: 60px;
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 16px;
}

.brand {
  font-size: 18px;
  font-weight: 700;
  color: $color-primary;
}

.nav {
  display: flex;
  gap: 16px;
  flex: 1;
}

.nav-item {
  color: $color-text-secondary;
  font-size: 14px;
}

.nav-item.router-link-exact-active {
  color: $color-primary;
  font-weight: 600;
}

.account {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nickname {
  font-size: 14px;
  color: $color-text;
}

.layout-main { flex: 1; }

.layout-footer {
  text-align: center;
  padding: 24px 16px;
}
</style>