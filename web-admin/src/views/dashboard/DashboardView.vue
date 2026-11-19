<template>
  <div>
    <h2 class="title">概览</h2>
    <p class="muted">
      数字只显示接口真能给的：已有接口的照明数字并标「已接入」，还没有接口的显示「—」并标「待接入」，不放假数据。
    </p>

    <el-row :gutter="16" class="cards">
      <el-col :span="6" v-for="c in cards" :key="c.name">
        <component
          :is="c.to ? 'router-link' : 'div'"
          :to="c.to"
          class="stat"
          :class="{ clickable: !!c.to }"
        >
          <div class="stat-name muted">{{ c.name }}</div>
          <div class="stat-value">{{ c.value }}</div>
          <el-tag size="small" :type="c.ready ? 'success' : 'info'">
            {{ c.ready ? '已接入' : '待接入' }}
          </el-tag>
          <span v-if="c.to" class="stat-link">去处理 →</span>
        </component>
      </el-col>
    </el-row>

    <div class="block">
      <h3>逾期扫描</h3>
      <p class="muted">
        答辩演示用的「立即执行逾期扫描」：点一下就把「已过归还日但仍使用中」的订单置为 OVERDUE，
        不用等定时任务（定时任务读 <code>sys_config['order.scan.cron']</code>，两者调用同一个方法）。
      </p>
      <div class="scan-row">
        <el-button type="primary" :loading="scanning" @click="runScan">立即执行逾期扫描</el-button>
        <span v-if="scanText" class="muted">{{ scanText }}</span>
      </div>
    </div>

    <div class="block">
      <h3>管理端工作台进度</h3>
      <el-table :data="modules" size="small">
        <el-table-column prop="name" label="模块" width="160" />
        <el-table-column prop="desc" label="内容" />
        <el-table-column prop="slice" label="切片" width="100" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.ready" size="small" type="success">已可用</el-tag>
            <el-tag v-else size="small" type="info">待开发</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="入口" width="100">
          <template #default="{ row }">
            <router-link v-if="row.to" :to="row.to">打开</router-link>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listAdminItems } from '@/api/item'
import request from '@/api/request'

const scanning = ref(false)
const scanText = ref('')

// 只有"已经有接口"的数字才照亮，其余保持「—」，绝不放假数据（项目进度.md §3.1）
const cards = ref([
  { name: '待审核物品', value: '—', ready: false, to: '/audit' },
  { name: '在架物品', value: '—', ready: false, to: '/audit' },
  { name: '进行中订单', value: '—', ready: false, to: null },
  { name: '逾期订单', value: '—', ready: false, to: null }
])

const modules = [
  { name: '物品审核', desc: '待审核列表 + 通过/驳回（驳回必须写原因）+ 详情抽屉', slice: '切片 2', ready: true, to: '/audit' },
  { name: '订单与逾期扫描', desc: '状态流转已通（确认/取用/取消），管理端可立即执行逾期扫描', slice: '切片 4', ready: true, to: null },
  { name: '申诉处理', desc: '申诉列表、裁定、信誉回滚', slice: '切片 5', ready: false, to: null },
  { name: '参数配置', desc: '信誉/费用/损耗策略读写 + 立即生效刷新', slice: '切片 6', ready: false, to: null },
  { name: '数据看板', desc: 'ECharts 图表（需后端统计接口）', slice: '切片 6', ready: false, to: null }
]

/** 手动触发逾期扫描：与定时任务调用同一个后端方法，不存在两套逻辑 */
async function runScan() {
  scanning.value = true
  try {
    const result = await request.post('/admin/orders/scan-overdue')
    scanText.value = result.updated > 0
      ? `本次 ${result.updated} 单转为「已逾期」（${result.scannedAt}）`
      : `扫描完成：没有需要转换的订单（${result.scannedAt}）`
    ElMessage.success(scanText.value)
  } catch {
    // 提示已由 request.js 统一弹出
  } finally {
    scanning.value = false
  }
}

onMounted(async () => {  try {
    const pending = await listAdminItems({ status: 'PENDING', pageNum: 1, pageSize: 1 })
    cards.value[0].value = String(pending.total ?? 0)
    cards.value[0].ready = true
  } catch (e) {
    // 提示已由 request.js 统一弹出；保持「—」比显示 0 更诚实
  }
  try {
    const page = await request.get('/items', { params: { pageNum: 1, pageSize: 1 } })
    cards.value[1].value = String(page.total ?? 0)
    cards.value[1].ready = true
  } catch (e) {
    // 同上
  }
})
</script>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.title {
  margin: 0 0 $space-1;
}
.cards {
  margin: $space-4 0;
}
.stat {
  display: block;
  background: $color-surface;
  border: 1px solid $color-border;
  border-radius: $radius-base;
  padding: $space-4;
  color: inherit;
  text-decoration: none;
  transition: border-color 0.2s;

  &.clickable:hover {
    border-color: $color-primary;
  }
}
.stat-name {
  margin-bottom: $space-2;
  font-size: $font-size-sm;
}
.stat-value {
  font-size: $font-size-xl;
  font-weight: 700;
  margin-bottom: $space-2;
}
.stat-link {
  margin-left: $space-2;
  font-size: $font-size-xs;
  color: $color-primary;
}
.block {
  background: $color-surface;
  border: 1px solid $color-border;
  border-radius: $radius-base;
  padding: $space-4;
  margin-top: $space-4;
}
.scan-row {
  display: flex;
  align-items: center;
  gap: $space-3;
  margin-top: $space-2;
}
</style>
