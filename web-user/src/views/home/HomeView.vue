<template>
  <div class="page-container">
    <section class="hero card">
      <h1 class="hero-title">邻里之间，借来借去</h1>
      <p class="muted hero-sub">同小区优先 · 按时归还 · 押金按损耗退</p>
      <div class="hero-actions">
        <el-button v-if="!userStore.isLogin" type="primary" @click="router.push({ name: 'register' })">注册开始使用</el-button>
        <el-button v-else type="primary" @click="router.push({ name: 'mine' })">进入我的主页</el-button>
        <el-button @click="scrollToBoard">查看开发进度</el-button>
      </div>
    </section>

    <section class="card board" id="board">
      <h2 class="section-title">功能进度（前后端一起交付）</h2>
      <el-table :data="slices" size="small" style="width: 100%">
        <el-table-column prop="name" label="切片" width="120" />
        <el-table-column prop="demo" label="你现在能在这里做什么" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.done ? 'success' : 'info'" size="small">{{ row.done ? '已可用' : '开发中' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <p class="muted tip">物品与订单尚未开放：后端接口写完一个，这里就开放一个入口，不做假数据。</p>
    </section>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const slices = [
  { name: '账号', demo: '注册 / 登录 / 改昵称 / 传头像 / 退出', done: true },
  { name: '物品', demo: '发布闲置、按小区浏览、管理员审核', done: false },
  { name: '下单', demo: '选日期、看是否被占、冲突时给建议', done: false },
  { name: '订单流转', demo: '确认 / 取用 / 归还 / 取消', done: false },
  { name: '结算与信誉', demo: '租金逾期损耗明细、信誉分变化', done: false },
  { name: '推荐与看板', demo: '带理由的推荐、管理端统计', done: false }
]

function scrollToBoard() {
  document.getElementById('board')?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.hero { padding: 40px 28px; text-align: center; }
.hero-title { margin: 0 0 8px; font-size: 28px; color: $color-primary; }
.hero-sub { margin: 0 0 24px; }
.hero-actions { display: flex; gap: 12px; justify-content: center; }

.board { margin-top: 20px; }
.section-title { margin: 0 0 16px; font-size: 18px; }
.tip { margin: 12px 0 0; }
</style>