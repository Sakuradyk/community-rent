<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createOrder, quoteOrder } from '@/api/order'
import { getItem } from '@/api/item'
import { getWallet } from '@/api/wallet'
import { CODE } from '@/utils/constants'
import { formatMoney, resolveImageUrl } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const itemId = route.query.itemId
const item = ref(null)
const loading = ref(true)
const loadError = ref('')

const form = ref({ dates: [], remark: '' })
const quote = ref(null)
const quoteLoading = ref(false)

/** 4090 的 data：{ itemId, conflictDates: [], suggestRanges: [{ startDate, endDate }] } */
const conflict = ref(null)
const submitting = ref(false)

// 可用余额：只用于提示与置灰，真正的裁决仍在后端（冻结失败会返回 4005）
const availableBalance = ref(0)
const balanceShort = computed(() => {
  if (!quote.value) return false
  return Number(availableBalance.value) < Number(quote.value.totalAmount || 0)
})

async function loadBalance() {
  try {
    const data = await getWallet()
    availableBalance.value = Number(data?.cashBalance || 0)
  } catch (e) {
    // 余额拿不到就不拦提交，交给后端判定
  }
}

const rentDays = computed(() => {
  if (!quote.value) return 0
  return quote.value.rentDays
})

function todayString() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 前端只挡"过去的日子"，冲突一律交给后端（规则 F1：前端不算冲突） */
function disabledDate(date) {
  return date.getTime() < new Date(todayString()).getTime()
}

async function loadItem() {
  try {
    item.value = await getItem(itemId)
  } catch (e) {
    loadError.value = e?.message || '物品加载失败'
  } finally {
    loading.value = false
  }
}

async function refreshQuote() {
  conflict.value = null
  if (!form.value.dates || form.value.dates.length !== 2) {
    quote.value = null
    return
  }
  quoteLoading.value = true
  try {
    quote.value = await quoteOrder({
      itemId,
      startDate: form.value.dates[0],
      endDate: form.value.dates[1],
    })
  } catch (e) {
    quote.value = null
    if (e?.code === CODE.OCCUPANCY_CONFLICT) {
      conflict.value = e.data
    }
  } finally {
    quoteLoading.value = false
  }
}

watch(() => form.value.dates, refreshQuote, { deep: true })

function applyRange(range) {
  form.value.dates = [range.startDate, range.endDate]
}

async function submit() {
  if (!form.value.dates || form.value.dates.length !== 2) {
    ElMessage.warning('请先选择租用日期')
    return
  }
  if (!quote.value) {
    ElMessage.warning('当前日期还不能下单，请先解决时段冲突')
    return
  }
  submitting.value = true
  try {
    const created = await createOrder({
      itemId,
      startDate: form.value.dates[0],
      endDate: form.value.dates[1],
      remark: form.value.remark || null,
    })
    ElMessage.success(`下单成功，订单号 ${created.orderNo}，等待出租方确认`)
    router.replace({ name: 'order-detail', params: { id: created.id } })
  } catch (e) {
    if (e?.code === CODE.OCCUPANCY_CONFLICT) {
      conflict.value = e.data
      quote.value = null
      ElMessage.error('这段时间刚被别人租走了，试试推荐时段')
    }
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadItem()
  loadBalance()
})
</script>

<template>
  <div class="page-container order-create">
    <el-page-header class="back" @back="router.back()">
      <template #content><span class="title">填写租用信息</span></template>
    </el-page-header>

    <el-skeleton v-if="loading" :rows="6" animated />
    <el-empty v-else-if="loadError || !item" :description="loadError || '物品不存在或还在审核中'" />

    <div v-else class="layout">
      <section class="card item-panel">
        <img class="cover" :src="resolveImageUrl(item.images?.[0]?.url)" :alt="item.title" />
        <div class="item-info">
          <h2>{{ item.title }}</h2>
          <p class="muted">{{ item.categoryName }} · {{ item.communityName }}</p>
          <p class="muted">出租人：{{ item.owner.nickname }}（信誉分 {{ item.owner.creditScore }}）</p>
          <div class="price-line">
            <span class="price">¥{{ formatMoney(item.dailyPrice) }}</span>
            <span class="muted">/ 天</span>
            <span class="deposit">押金 ¥{{ formatMoney(item.deposit) }}</span>
          </div>
          <p class="muted small">
            逾期费率 {{ item.overdueRate }} 倍/天（前 3 天有折扣策略）；费用一律由后端计算。
          </p>
        </div>
      </section>

      <section class="card form-panel">
        <el-form label-position="top">
          <el-form-item label="租用日期">
            <el-date-picker
              v-model="form.dates"
              type="daterange"
              value-format="YYYY-MM-DD"
              start-placeholder="起租日（含）"
              end-placeholder="归还日（不含）"
              :disabled-date="disabledDate"
              style="width: 100%"
            />
            <p class="muted small">归还日当天不算租金，即 [起租日, 归还日) 半开区间。</p>
          </el-form-item>
          <el-form-item label="备注（可选）">
            <el-input
              v-model="form.remark"
              type="textarea"
              :rows="3"
              maxlength="255"
              show-word-limit
              placeholder="例如：周五晚上七点后来取，谢谢"
            />
          </el-form-item>
        </el-form>

        <el-alert
          v-if="conflict"
          class="conflict"
          type="warning"
          :closable="false"
          show-icon
          title="所选时段已被别人租走"
        >
          <p class="small">冲突日期：{{ (conflict.conflictDates || []).join('、') }}</p>
          <div v-if="conflict.suggestRanges?.length" class="suggest">
            <span class="small">试试这几天：</span>
            <el-button
              v-for="(r, i) in conflict.suggestRanges"
              :key="i"
              size="small"
              plain
              @click="applyRange(r)"
            >
              {{ r.startDate }} → {{ r.endDate }}
            </el-button>
          </div>
        </el-alert>

        <div class="quote" v-if="quote">
          <div class="row"><span>日租金 × 天数</span><span>¥{{ formatMoney(quote.dailyPrice) }} × {{ rentDays }} 天</span></div>
          <div class="row"><span>租金</span><span>¥{{ formatMoney(quote.rentAmount) }}</span></div>
          <div class="row"><span>押金（归还后退）</span><span>¥{{ formatMoney(quote.depositAmount) }}</span></div>
          <div class="row total"><span>合计</span><span>¥{{ formatMoney(quote.totalAmount) }}</span></div>
          <div class="row">
            <span>本次将冻结</span>
            <span>¥{{ formatMoney(quote.totalAmount) }}（归还结算后多退少补）</span>
          </div>
          <div class="row">
            <span>我的可用余额</span>
            <span :class="{ 'balance-short': balanceShort }">¥{{ formatMoney(availableBalance) }}</span>
          </div>
        </div>
        <p v-else-if="quoteLoading" class="muted small">正在试算…</p>

        <el-alert
          v-if="balanceShort"
          type="warning"
          :closable="false"
          show-icon
          title="可用余额不足本次冻结金额"
          description="去「我的」页面充值后再提交（充值为演示功能，点确认即到账）。"
          style="margin-bottom: 12px"
        />

        <div class="actions">
          <el-button
            type="primary"
            size="large"
            :loading="submitting"
            :disabled="!quote || balanceShort"
            @click="submit"
          >
            提交订单
          </el-button>
          <el-button size="large" @click="router.back()">取消</el-button>
          <el-button v-if="balanceShort" size="large" @click="router.push({ name: 'mine' })">去充值</el-button>
        </div>
        <p class="muted small">
          提交后订单进入「待确认」，并立即占用所选日期；同一时段只会有一个人成功。
        </p>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.back {
  margin-bottom: $space-4;
}

.title {
  font-size: $font-size-lg;
  font-weight: 600;
}

.layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $space-6;
  align-items: start;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.item-panel {
  display: flex;
  gap: $space-4;
  padding: $space-4;
}

.cover {
  width: 160px;
  height: 160px;
  object-fit: cover;
  border-radius: $radius-base;
  background: $color-surface-sunken;
  flex: 0 0 auto;
}

.item-info h2 {
  margin: 0 0 $space-2;
  font-size: $font-size-lg;
}

.price-line {
  margin: $space-3 0 $space-1;
  display: flex;
  align-items: baseline;
  gap: $space-2;

  .price {
    color: $color-primary-deep;
    font-size: $font-size-xl;
    font-weight: 700;
  }

  .deposit {
    margin-left: $space-4;
    color: $color-text-secondary;
  }
}

.form-panel {
  padding: $space-6;
}

.small {
  font-size: $font-size-xs;
}

.conflict {
  margin-bottom: $space-4;
}

.suggest {
  margin-top: $space-2;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: $space-2;
}

.quote {
  border: 1px solid $color-border;
  border-radius: $radius-base;
  padding: $space-4;
  margin-bottom: $space-4;
  background: $color-primary-wash;

  .row {
    display: flex;
    justify-content: space-between;
    padding: $space-1 0;
    color: $color-text-secondary;

    &.total {
      border-top: 1px dashed $color-border-strong;
      margin-top: $space-2;
      padding-top: $space-3;
      color: $color-text;
      font-size: $font-size-lg;
      font-weight: 700;
    }
  }
}

.actions {
  display: flex;
  gap: $space-3;
  margin-bottom: $space-2;
}
</style>
<style scoped lang="scss">

@use '@/assets/styles/variables.scss' as *;.balance-short {
  color: -danger;
  font-weight: 600;
}
</style>