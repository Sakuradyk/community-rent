<script setup>
import { computed, onMounted, ref } from 'vue'
import { listCredits } from '@/api/credit'
import { useUserStore } from '@/stores/user'
import { LIMITS } from '@/utils/constants'
import { formatDateTime } from '@/utils/format'

const userStore = useUserStore()

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(LIMITS.PAGE_SIZE)
const loading = ref(false)

/** 后端只给码，中文文案在页面上配。新增码必须同步这里与后端 CreditService */
const REASON_LABEL = {
  ON_TIME: '按时履约',
  OVERDUE_LIGHT: '逾期还款（轻）',
  OVERDUE_HEAVY: '逾期还款（重）',
  DAMAGE: '物品损坏',
  CANCEL_AFTER_CONFIRM: '确认后取消',
  ADMIN_ADJUST: '管理员调整'
}

const score = computed(() => userStore.profile?.creditScore ?? 0)

/** 信誉档位（与 credit_policy 的阈值一致：免押 ≥95、正常 60–94、受限 40–59、暂停 <40） */
const tier = computed(() => {
  const value = score.value
  if (value >= 95) return { label: '免押档', hint: '租用无需押金', type: 'success' }
  if (value >= 60) return { label: '正常档', hint: '按物品押金规则收取', type: 'primary' }
  if (value >= 40) return { label: '受限档', hint: '押金上浮 1.5 倍', type: 'warning' }
  return { label: '暂停档', hint: '暂不可下单', type: 'danger' }
})

const progress = computed(() => Math.min(100, Math.max(0, score.value)))

async function load() {
  loading.value = true
  try {
    const page = await listCredits({ pageNum: pageNum.value, pageSize: pageSize.value })
    rows.value = page?.list || []
    total.value = page?.total || 0
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onPageChange(page) {
  pageNum.value = page
  load()
}

onMounted(async () => {
  if (userStore.isLogin) await userStore.fetchMe()
  await load()
})
</script>

<template>
  <div class="page-container">
    <section class="card score-card">
      <div class="score-main">
        <span class="muted">我的信誉分</span>
        <div class="score-value">
          {{ score }}
          <span class="score-max">/ 100</span>
        </div>
        <el-tag :type="tier.type" effect="plain">{{ tier.label }}</el-tag>
        <span class="muted small">{{ tier.hint }}</span>
      </div>
      <el-progress
        class="score-bar"
        :percentage="progress"
        :stroke-width="10"
        :show-text="false"
        :color="score >= 95 ? '#15803d' : score >= 60 ? '#ea580c' : score >= 40 ? '#b45309' : '#b91c1c'"
      />
      <p class="muted small">
        初始 80 分，区间 [0,100]。按时履约 +2（每日最多 +4）；逾期每天 −5（超过 3 天每天 −10）；损坏 −10；出租方确认后取消 −3。
      </p>
    </section>

    <section class="card logs">
      <h3>信誉流水</h3>
      <el-table v-loading="loading" :data="rows" size="small">
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="原因" width="160">
          <template #default="{ row }">{{ REASON_LABEL[row.reasonCode] || row.reasonCode }}</template>
        </el-table-column>
        <el-table-column label="变动" width="100">
          <template #default="{ row }">
            <span :class="row.change > 0 ? 'up' : 'down'">{{ row.change > 0 ? '+' : '' }}{{ row.change }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分数变化" width="140">
          <template #default="{ row }">{{ row.beforeScore }} → {{ row.afterScore }}</template>
        </el-table-column>
        <el-table-column prop="reasonDesc" label="说明" />
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="total"
          :page-size="pageSize"
          :current-page="pageNum"
          @current-change="onPageChange"
        />
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.score-card {
  padding: $space-6;
  margin-bottom: $space-6;
}

.score-main {
  display: flex;
  align-items: baseline;
  gap: $space-3;
  flex-wrap: wrap;
}

.score-value {
  font-size: 40px;
  font-weight: 700;
  color: $color-primary-deep;
  line-height: 1;
}

.score-max {
  font-size: $font-size-sm;
  color: $color-text-secondary;
  font-weight: 400;
}

.score-bar {
  margin: $space-4 0 $space-2;
}

.logs {
  padding: $space-4;

  h3 {
    margin: 0 0 $space-3;
    font-size: $font-size-md;
  }
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: $space-4;
}

.small {
  font-size: $font-size-xs;
}

.up {
  color: #15803d;
  font-weight: 600;
}

.down {
  color: #b91c1c;
  font-weight: 600;
}
</style>
