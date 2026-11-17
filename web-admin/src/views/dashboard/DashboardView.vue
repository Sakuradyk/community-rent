<template>
  <div>
    <h2 class="title">概览</h2>
    <p class="muted">下面是管理端将要承载的四块工作。**没有接口就不放假数字**，接口上线即点亮。</p>

    <el-row :gutter="16" class="cards">
      <el-col :span="6" v-for="c in cards" :key="c.name">
        <div class="stat">
          <div class="stat-name muted">{{ c.name }}</div>
          <div class="stat-value">{{ c.value }}</div>
          <el-tag size="small" :type="c.ready ? 'success' : 'info'">{{ c.ready ? '已接入' : '待接入' }}</el-tag>
        </div>
      </el-col>
    </el-row>

    <div class="block">
      <h3>逾期扫描</h3>
      <p class="muted">答辩演示需要「立即执行逾期扫描」按钮：点一下就把到期未还的订单置为 OVERDUE，不用等定时任务。</p>
      <el-button type="primary" disabled>立即执行逾期扫描（待接口）</el-button>
    </div>

    <div class="block">
      <h3>管理端待做清单</h3>
      <el-table :data="todos" size="small">
        <el-table-column prop="name" label="页面" width="160" />
        <el-table-column prop="desc" label="内容" />
        <el-table-column prop="slice" label="切片" width="100" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
const cards = [
  { name: '待审核物品', value: '—', ready: false },
  { name: '进行中订单', value: '—', ready: false },
  { name: '待处理申诉', value: '—', ready: false },
  { name: '逾期订单', value: '—', ready: false }
]

const todos = [
  { name: '物品审核', desc: '列表 + 通过/驳回 + 驳回原因回填', slice: '切片 2' },
  { name: '订单与逾期扫描', desc: '订单列表、状态筛选、立即执行逾期扫描', slice: '切片 4' },
  { name: '申诉处理', desc: '申诉列表、裁定、信誉回滚', slice: '切片 5' },
  { name: '参数配置', desc: '信誉/费用/损耗策略读写 + 立即生效刷新', slice: '切片 6' },
  { name: '数据看板', desc: 'ECharts 图表（需后端统计接口）', slice: '切片 6' }
]
</script>

<style scoped lang="scss">
.title { margin: 0 0 8px; }
.cards { margin: 20px 0; }
.stat {
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  padding: 16px;
}
.stat-name { margin-bottom: 8px; }
.stat-value { font-size: 26px; font-weight: 700; margin-bottom: 8px; }
.block { background: #fff; border: 1px solid #dcdfe6; border-radius: 8px; padding: 20px; margin-top: 16px; }
</style>