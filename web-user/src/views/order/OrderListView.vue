<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listOrders } from '@/api/order'
import { LIMITS, ORDER_STATUS, statusOf } from '@/utils/constants'
import { formatDate, formatDateTime, formatMoney, resolveImageUrl } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const role = ref(route.query.role === 'owner' ? 'owner' : 'renter')
const status = ref('')
const pageNum = ref(1)
const pageSize = ref(LIMITS.PAGE_SIZE)
const total = ref(0)
const list = ref([])
const loading = ref(false)

const statusOptions = computed(() =>
  Object.entries(ORDER_STATUS).map(([value, meta]) => ({ value, label: meta.label })),
)

const roleTabs = [
  { label: '我租的', value: 'renter' },
  { label: '我出租的', value: 'owner' },
]

const statusMeta = (s) => statusOf(ORDER_STATUS, s)

async function load() {
  loading.value = true
  try {
    const page = await listOrders({
      role: role.value,
      status: status.value || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    list.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  pageNum.value = 1
  load()
}

watch([role, status], resetAndLoad)
watch(pageNum, load)

onMounted(load)
</script>

<template>
  <div class="page-container orders">
    <header class="head">
      <h1>我的订单</h1>
      <p class="muted">「我租的」是别人把东西租给你；「我出租的」是你的物品被租走。点任意一行看详情。</p>
    </header>

    <div class="filters card">
      <el-radio-group v-model="role">
        <el-radio-button v-for="tab in roleTabs" :key="tab.value" :value="tab.value">
          {{ tab.label }}
        </el-radio-button>
      </el-radio-group>
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 160px">
        <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <span class="muted small">共 {{ total }} 单</span>
    </div>

    <el-skeleton v-if="loading" :rows="5" animated />
    <el-empty v-else-if="!list.length" description="还没有订单。去「逛物品」挑一件试试？">
      <el-button type="primary" @click="router.push({ name: 'items' })">去逛物品</el-button>
    </el-empty>

    <div v-else class="rows">
      <article
        v-for="order in list"
        :key="order.id"
        class="card row"
        @click="router.push({ name: 'order-detail', params: { id: order.id } })"
      >
        <img class="cover" :src="resolveImageUrl(order.itemCoverUrl)" :alt="order.itemTitle" />
        <div class="main">
          <div class="line-1">
            <strong>{{ order.itemTitle }}</strong>
            <el-tag :color="statusMeta(order.status).color" effect="dark" size="small">
              {{ order.statusLabel }}
            </el-tag>
          </div>
          <p class="muted small">
            {{ formatDate(order.startDate) }} → {{ formatDate(order.endDate) }}（{{ order.rentDays }} 天）
            · {{ order.orderNo }}
          </p>
          <p class="muted small">
            {{ order.role === 'renter' ? '出租方' : '承租方' }}：{{ order.counterpartyNickname }}
            · 下单于 {{ formatDateTime(order.createdAt) }}
          </p>
        </div>
        <div class="amount">
          <span class="price">¥{{ formatMoney(order.totalAmount) }}</span>
          <span class="muted small">含押金 ¥{{ formatMoney(order.depositAmount) }}</span>
        </div>
      </article>
    </div>

    <el-pagination
      v-if="total > pageSize"
      v-model:current-page="pageNum"
      class="pager"
      background
      layout="prev, pager, next, total"
      :page-size="pageSize"
      :total="total"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.head {
  margin-bottom: $space-4;

  h1 {
    margin: 0 0 $space-1;
    font-size: $font-size-xl;
  }
}

.filters {
  display: flex;
  align-items: center;
  gap: $space-3;
  padding: $space-3 $space-4;
  margin-bottom: $space-4;
}

.small {
  font-size: $font-size-xs;
}

.rows {
  display: flex;
  flex-direction: column;
  gap: $space-3;
}

.row {
  display: flex;
  gap: $space-4;
  align-items: center;
  padding: $space-3 $space-4;
  cursor: pointer;
  transition: box-shadow 0.2s ease, transform 0.2s ease;

  &:hover {
    box-shadow: $shadow-lg;
    transform: translateY(-1px);
  }
}

.cover {
  width: 88px;
  height: 88px;
  object-fit: cover;
  border-radius: $radius-sm;
  background: $color-surface-sunken;
}

.main {
  flex: 1;
  min-width: 0;

  .line-1 {
    display: flex;
    align-items: center;
    gap: $space-2;
    margin-bottom: $space-1;
  }
}

.amount {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: $space-1;

  .price {
    color: $color-primary-deep;
    font-size: $font-size-lg;
    font-weight: 700;
  }
}

.pager {
  margin-top: $space-4;
  justify-content: flex-end;
}
</style>
