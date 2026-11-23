<template>
  <div class="page-container">
    <div class="card">
      <div class="head">
        <div>
          <div class="title">我的账单</div>
          <div class="muted small">每一次冻结、解冻、结算与充值都会留一条分录，金额与余额快照都由后端给</div>
        </div>
        <div class="balance">
          <span class="muted small">可用 ¥{{ money(wallet.cashBalance) }}</span>
          <span class="muted small">冻结 ¥{{ money(wallet.frozenBalance) }}</span>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" empty-text="还没有资金记录" size="default">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="动作" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="actionTag(row.action)">{{ row.actionLabel || row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="130" align="right">
          <template #default="{ row }">
            <span :class="['amount', directionClass(row)]">
              {{ directionSign(row) }}{{ money(row.amount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="余额快照" width="130" align="right">
          <template #default="{ row }">¥{{ money(row.balanceAfter) }}</template>
        </el-table-column>
        <el-table-column label="说明" min-width="220">
          <template #default="{ row }">
            <span class="muted">{{ row.remark || '—' }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          :page-size="query.pageSize"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { getWallet, listWalletEntries } from '@/api/wallet'
import { LIMITS } from '@/utils/constants'
import { formatDateTime } from '@/utils/format'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const wallet = reactive({ cashBalance: 0, frozenBalance: 0, totalBalance: 0 })
const query = reactive({ pageNum: 1, pageSize: LIMITS.PAGE_SIZE })

const DIRECTION_TAGS = {
  IN: 'success',
  OUT: 'danger',
  MOVE: 'info'
}

const ACTION_TAGS = {
  RECHARGE: 'success',
  FREEZE: 'warning',
  RELEASE: 'info',
  RENT: 'primary',
  OVERDUE: 'danger',
  LOSS: 'danger',
  REFUND: 'success',
  ADJUST: 'warning'
}

function money(v) {
  return Number(v || 0).toFixed(2)
}

function actionTag(action) {
  return ACTION_TAGS[action] || 'info'
}

function directionClass(row) {
  if (row.direction === 'IN') return 'in'
  if (row.direction === 'OUT') return 'out'
  return 'move'
}

function directionSign(row) {
  if (row.direction === 'IN') return '+'
  if (row.direction === 'OUT') return '-'
  return ''
}

async function load() {
  loading.value = true
  try {
    const data = await listWalletEntries({ pageNum: query.pageNum, pageSize: query.pageSize })
    rows.value = data?.list || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load()
  try {
    const data = await getWallet()
    if (data) {
      wallet.cashBalance = data.cashBalance
      wallet.frozenBalance = data.frozenBalance
      wallet.totalBalance = data.totalBalance
    }
  } catch (e) {
    // 余额失败不阻塞流水
  }
})
</script>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;
.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: $space-3;
  margin-bottom: $space-3;
}

.title {
  font-size: 18px;
  font-weight: 600;
  color: $color-text;
}

.balance {
  display: flex;
  gap: $space-3;
}

.amount {
  font-weight: 600;
  font-variant-numeric: tabular-nums;

  &.in {
    color: $color-success;
  }

  &.out {
    color: $color-danger;
  }

  &.move {
    color: $color-text-secondary;
  }
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: $space-3;
}
</style>
