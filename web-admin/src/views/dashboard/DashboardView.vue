<template>
  <div>
    <h2 class="title">数据看板</h2>
    <p class="muted">
      数字全部来自 <code>/api/admin/stats</code>（后端实时聚合，不是缓存快照）；
      GMV 只统计已完成订单的租金，不含押金。
    </p>

    <el-row :gutter="16" class="cards" v-loading="loading">
      <el-col :span="6" v-for="c in cards" :key="c.name">
        <component
          :is="c.to ? 'router-link' : 'div'"
          :to="c.to"
          class="stat"
          :class="{ clickable: !!c.to }"
        >
          <div class="stat-name muted">{{ c.name }}</div>
          <div class="stat-value">{{ c.value }}</div>
          <div class="stat-sub muted">{{ c.sub }}</div>
        </component>
      </el-col>
    </el-row>

    <div class="charts">
      <div class="chart-card">
        <header class="chart-head">
          <h3>订单与成交趋势</h3>
          <el-radio-group v-model="days" size="small" @change="loadTrend">
            <el-radio-button :value="7">7 天</el-radio-button>
            <el-radio-button :value="14">14 天</el-radio-button>
            <el-radio-button :value="30">30 天</el-radio-button>
          </el-radio-group>
        </header>
        <div ref="trendRef" class="chart"></div>
      </div>

      <div class="chart-card">
        <header class="chart-head">
          <h3>订单状态分布</h3>
          <span class="muted">共 {{ overview.orderTotal }} 单</span>
        </header>
        <div ref="pieRef" class="chart"></div>
      </div>
    </div>

    <div class="block">
      <h3>扫描任务（答辩演示用，不等定时任务到点）</h3>
      <p class="muted">
        三个按钮各自调用与定时任务<b>同一个后端方法</b>，不存在两套逻辑：
        逾期扫描把「已过归还日但仍使用中」的订单置为已逾期；
        冷静期扫描把「超过 48 小时未表态」的结算单自动确认；
        归还超时扫描把「承租方已申报归还、出租方超 24 小时未验收」的订单按申报自动结算。
      </p>
      <div class="scan-row">
        <el-button type="primary" :loading="scanning" @click="runScan('/admin/orders/scan-overdue', '逾期')">
          立即执行逾期扫描
        </el-button>
        <el-button type="primary" plain :loading="settling" @click="runScan('/admin/settlements/scan-confirm', '结算冷静期')">
          立即执行冷静期扫描
        </el-button>
        <el-button type="primary" plain :loading="returning" @click="runScan('/admin/orders/scan-return', '归还超时')">
          立即执行归还超时扫描
        </el-button>
      </div>
      <p v-if="scanText" class="muted">{{ scanText }}</p>
    </div>

    <div class="block">
      <h3>管理端功能</h3>
      <el-table :data="modules" size="small">
        <el-table-column prop="name" label="模块" width="160" />
        <el-table-column prop="desc" label="内容" />
        <el-table-column prop="slice" label="切片" width="100" />
        <el-table-column label="入口" width="100">
          <template #default="{ row }">
            <router-link v-if="row.to" :to="row.to">打开</router-link>
            <span v-else class="muted">本页</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { getOverview, getTrend } from '@/api/stats'
import request from '@/api/request'

const loading = ref(false)
const days = ref(7)
const overview = ref({})
const cards = ref([
  { name: '待审核物品', value: '—', sub: '需要人工过一遍', to: '/audit' },
  { name: '进行中订单', value: '—', sub: '已确认 / 使用中 / 已逾期', to: null },
  { name: '待裁决申诉', value: '—', sub: '结算纠纷', to: '/appeals' },
  { name: '待确认结算', value: '—', sub: '冷静期内', to: null }
])

const scanning = ref(false)
const settling = ref(false)
const returning = ref(false)
const scanText = ref('')

const modules = [
  { name: '物品审核', desc: '待审核列表 + 通过/驳回（驳回必须写原因）+ 详情抽屉', slice: '切片 2', to: '/audit' },
  { name: '申诉裁决', desc: '四档裁定（支持承租方 / 支持出租方 / 部分支持 / 驳回）+ 证据图', slice: '切片 5', to: '/appeals' },
  { name: '参数配置', desc: '信誉/费用/损耗三张策略表 + sys_config 键值，保存即生效（后端不缓存）', slice: '切片 6', to: '/config' },
  { name: '扫描任务', desc: '逾期 / 结算冷静期 / 归还超时三个扫描，本页可直接触发', slice: '切片 4–5', to: null }
]

const trendRef = ref(null)
const pieRef = ref(null)
let trendChart = null
let pieChart = null

const STATUS_LABELS = {
  PENDING: '待确认',
  CONFIRMED: '已确认',
  IN_USE: '使用中',
  OVERDUE: '已逾期',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

async function loadOverview() {
  loading.value = true
  try {
    const data = await getOverview()
    overview.value = data
    cards.value = [
      { name: '待审核物品', value: data.itemPendingAudit, sub: `在架 ${data.itemOnShelf} / 共 ${data.itemTotal}`, to: '/audit' },
      {
        name: '进行中订单',
        value: data.orderConfirmed + data.orderInUse + data.orderOverdue,
        sub: `已确认 ${data.orderConfirmed} · 使用中 ${data.orderInUse} · 逾期 ${data.orderOverdue}`,
        to: null
      },
      { name: '待裁决申诉', value: data.appealPending, sub: '结算纠纷', to: '/appeals' },
      {
        name: '待确认结算',
        value: data.settlementPendingConfirm,
        sub: `GMV ¥${data.gmvTotal}（近 30 天 ¥${data.gmvIn30Days}）`,
        to: null
      }
    ]
    renderPie(data)
  } catch {
    // 提示已由 request.js 统一弹出
  } finally {
    loading.value = false
  }
}

async function loadTrend() {
  try {
    const data = await getTrend(days.value)
    renderTrend(data.points || [])
  } catch {
    // 同上
  }
}

function renderTrend(points) {
  if (!trendChart) return
  const dates = points.map((p) => p.date.slice(5))
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['订单数', '成交额'], right: 0 },
    grid: { left: 8, right: 8, top: 36, bottom: 4, containLabel: true },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: [
      { type: 'value', name: '单', minInterval: 1 },
      { type: 'value', name: '元' }
    ],
    series: [
      {
        name: '订单数',
        type: 'line',
        smooth: true,
        data: points.map((p) => p.orderCount),
        itemStyle: { color: '#ea580c' },
        areaStyle: { opacity: 0.12 }
      },
      {
        name: '成交额',
        type: 'bar',
        yAxisIndex: 1,
        data: points.map((p) => p.gmv),
        itemStyle: { color: '#f8cda8' },
        barMaxWidth: 18
      }
    ]
  })
}

function renderPie(data) {
  if (!pieChart) return
  const pairs = [
    ['PENDING', data.orderPending],
    ['CONFIRMED', data.orderConfirmed],
    ['IN_USE', data.orderInUse],
    ['OVERDUE', data.orderOverdue],
    ['COMPLETED', data.orderCompleted],
    ['CANCELLED', data.orderCancelled]
  ].filter(([, value]) => value > 0)

  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 单（{d}%）' },
    legend: { bottom: 0, icon: 'circle' },
    series: [
      {
        type: 'pie',
        radius: ['42%', '66%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        label: { formatter: '{b}\n{c}' },
        data: pairs.map(([status, value]) => ({ name: STATUS_LABELS[status] || status, value }))
      }
    ]
  })
}

/**
 * 手动触发扫描：三个扫描共用一套提示逻辑，只有 label 不同。
 * 返回 `{ updated, scannedAt }`，`updated=0` 也要给"扫完了但没事可做"的反馈。
 */
async function runScan(path, label) {
  const flag = path.includes('settlements') ? settling : path.includes('return') ? returning : scanning
  flag.value = true
  try {
    const result = await request.post(path)
    scanText.value = result.updated > 0
      ? `${label}扫描：本次 ${result.updated} 条被处理（${result.scannedAt}）`
      : `${label}扫描完成：没有需要处理的数据（${result.scannedAt}）`
    ElMessage.success(scanText.value)
    await loadOverview()
  } catch {
    // 提示已由 request.js 统一弹出
  } finally {
    flag.value = false
  }
}

function handleResize() {
  trendChart?.resize()
  pieChart?.resize()
}

onMounted(async () => {
  trendChart = echarts.init(trendRef.value)
  pieChart = echarts.init(pieRef.value)
  window.addEventListener('resize', handleResize)
  await Promise.all([loadOverview(), loadTrend()])
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  pieChart?.dispose()
  trendChart = null
  pieChart = null
})
</script>

<style scoped>
.title {
  margin: 0 0 4px;
  font-size: 18px;
}

.muted {
  color: #7a6a5d;
  font-size: 13px;
}

.cards {
  margin: 16px 0;
}

.stat {
  display: block;
  padding: 16px;
  background: #fff;
  border: 1px solid #efe4d8;
  border-radius: 8px;
  text-decoration: none;
  color: inherit;
}

.stat.clickable:hover {
  border-color: #ea580c;
}

.stat-name {
  font-size: 13px;
}

.stat-value {
  margin: 6px 0 4px;
  font-size: 26px;
  font-weight: 700;
  color: #33261d;
}

.stat-sub {
  font-size: 12px;
  line-height: 1.4;
}

.charts {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

@media (max-width: 1100px) {
  .charts {
    grid-template-columns: 1fr;
  }
}

.chart-card {
  padding: 16px;
  background: #fff;
  border: 1px solid #efe4d8;
  border-radius: 8px;
}

.chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.chart-head h3 {
  margin: 0;
  font-size: 14px;
}

.chart {
  height: 260px;
}

.block {
  padding: 16px;
  margin-bottom: 16px;
  background: #fff;
  border: 1px solid #efe4d8;
  border-radius: 8px;
}

.block h3 {
  margin: 0 0 8px;
  font-size: 14px;
}

.scan-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
}
</style>
