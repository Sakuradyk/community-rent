<template>
  <el-container class="admin">
    <el-aside width="200px" class="aside">
      <div class="brand">邻里租 · 管理端</div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/">概览</el-menu-item>
        <el-menu-item index="/audit">物品审核</el-menu-item>
    <el-menu-item index="/appeals">申诉裁决</el-menu-item>
      </el-menu>
      <div class="aside-foot muted">待接入：物品审核 / 配置 / 申诉</div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="muted">{{ currentPathTitle }}</span>
        <div class="header-right">
          <span v-if="adminStore.profile" class="nickname">{{ adminStore.profile.nickname }}</span>
          <el-button link type="primary" @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAdminStore } from '@/stores/admin'

const route = useRoute()
const router = useRouter()
const adminStore = useAdminStore()

const activeMenu = computed(() => route.path)
const currentPathTitle = computed(() => route.meta.title || '')

onMounted(async () => {
  try {
    await adminStore.fetchMe()
  } catch (e) {
    // 401 类由 request.js 处理
  }
})

async function handleLogout() {
  await adminStore.logout()
  ElMessage.success('已退出')
  router.push({ name: 'login' })
}
</script>

<style scoped lang="scss">
.admin { min-height: 100vh; }
.aside {
  background: #1f2329;
  color: #fff;
  display: flex;
  flex-direction: column;
}
.brand {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  font-weight: 600;
  color: #fff;
}
.menu {
  border-right: none;
  background: transparent;
  flex: 1;
}
.aside-foot { padding: 16px; }
.header {
  background: #fff;
  border-bottom: 1px solid #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-right { display: flex; align-items: center; gap: 12px; }
.nickname { font-size: 14px; }
</style>