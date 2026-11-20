<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { confirmSettlement, disputeSettlement, getSettlement } from '@/api/settlement'
import { FLOW_TYPE, LOSS_ACTION, RETURN_STATE, SETTLEMENT_STATUS, statusOf } from '@/utils/constants'
import { formatDate, formatDateTime, formatMoney } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const settlement = ref(null)
const loading = ref(true)
const acting = ref(false)
const errorText = ref('')
const now = ref(Date.now())

let timer = null

const statusMeta = computed(() => statusOf(SETTLEMENT_STATUS, settlement.value?.status))
const returnMeta = computed(() => {
  const value = settlement.value?.returnState
  return value ? statusOf(RETURN_STATE, value) : null
})
const actionMeta = computed(() => {
  const value = settlement.value?.action
  return value ? statusOf(LOSS_ACTION, value) : null
})

/** 冷静期倒计时：前端只做展示，能不能点由后端返回的 canConfirm / canDispute 决定 */
const remainMs = computed(() => {
  const deadline = settlement.value?.confirmDeadline
  if (!deadline) return 0
  return Math.max(0, new Date(String(deadline).replace(/-/g, '/')).getTime() - now.value)
})

const remainText = computed(() => {
  if (!settlement.value?.confirmDeadline) return '—'
  if (remainMs.value <= 0) return '已超过冷静期，等待系统自动确认'
  const hours = Math.floor(remainMs.value / 3_600_000)
  const minutes = Math.floor((remainMs.value % 3_600_000) / 60_000)
  return `剩余 ${hours} 小时 ${minutes} 分`
})

const flowRows = computed(() =>
  (settlement.value?.flows || []).map((flow) => ({
    ...flow,
    label: FLOW_TYPE[flow.type]?.label || flow.type,
  })),
)

async function load() {
  loading.value = true
  try {
    settlement.value = await getSettlement(route.params.orderId)
    errorText.value = ''
  } catch (e) {
    errorText.value = e?.message || '结算单加载失败'
  } finally {
    loading.value = false
  }
}

async function runAction(action, successText) {
  acting.value = true
  try {
    await action()
    ElMessage.success(successText)
    await load()
  } catch {
    // 失败提示由 request.js 统一弹出（3008 / 4003 / 3009 等）
  } finally {
    acting.value = false
  }
}

const onConfirm = () =>
  runAction(() => confirmSettlement(settlement.value.id), '已确认无异议，结算完成')

async function onDispute() {
  const result = await ElMessageBox.prompt(
    '请说明哪里不认可（例如：归还时就有划痕、逾期不是我造成的）。提交后由管理员裁决。',
    '提起异议',
    {
      confirmButtonText: '提交异议',
      cancelButtonText: '再想想',
      inputType: 'textarea',
      inputPlaceholder: '至少写清时间和现象',
      inputValidator: (value) => (value && value.trim().length >= 5) || '请至少写 5 个字说明理由',
    },
  ).catch(() => null)
  if (!result) return
  await runAction(
    () => disputeSettlement(settlement.value.id, { reason: result.value.trim() }),
    '异议已提交，等待管理员裁决',
  )
}

onMounted(() => {
  load()
  timer = setInterval(() => (now.value = Date.now()), 30_000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="page-container">
    <el-page-header class="back" @back="router.back()">
      <template #content><span class="title">结算单</span></template>
    </el-page-header>

    <el-skeleton v-if="loading" :rows="6" animated />
    <el-empty v-else-if="errorText || !settlement" :description="errorText || '还没有结算单（订单要先归还）'" />

    <template v-else>
      <section class="card head">
        <div>
          <el-tag :color="statusMeta.color" effect="dark" class="status-tag">
            {{ settlement.statusLabel }}
          </el-tag>
          <h2>结算单 {{ settlement.settleNo }}</h2>
          <p class="muted">
            生成于 {{ formatDateTime(settlement.createdAt) }}
            <template v-if="settlement.returnState">
              · 归还成色 {{ returnMeta?.label }}
            </template>
          </p>
        </div>
        <div class="refund">
          <span class="muted">应退押金</span>
          <strong>¥{{ formatMoney(settlement.refundDeposit) }}</strong>
        </div>
      </section>

      <el-alert
        v-if="settlement.status === 'PENDING_CONFIRM'"
        class="notice"
        type="warning"
        :closable="false"
        show-icon
        :title="`冷静期：${remainText}`"
        description="对金额没有异议就点「确认无异议」；有异议在倒计时结束前提出来，超时系统按无异议处理。"
      />
      <el-alert
        v-else-if="settlement.status === 'DISPUTED'"
        class="notice"
        type="info"
        :closable="false"
        show-icon
        title="已提交异议，等待管理员裁决"
        :description="settlement.remark || '裁决后这里会显示结论与调整后的金额。'"
      />
      <el-alert
        v-else
        class="notice"
        type="success"
        :closable="false"
        show-icon
        :title="settlement.status === 'ADJUSTED' ? '管理员裁决已调整金额' : '结算已生效'"
        :description="settlement.remark || `结算时间 ${formatDateTime(settlement.settledAt)}`"
      />

      <div class="grid">
        <section class="card">
          <h3>费用拆项</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="租金">¥{{ formatMoney(settlement.rentAmount) }}</el-descriptions-item>
            <el-descriptions-item label="押金">¥{{ formatMoney(settlement.depositAmount) }}</el-descriptions-item>
            <el-descriptions-item label="逾期天数">{{ settlement.overdueDays }} 天</el-descriptions-item>
            <el-descriptions-item label="逾期费">¥{{ formatMoney(settlement.overdueFee) }}</el-descriptions-item>
            <el-descriptions-item label="本次损耗率 w">{{ settlement.lossRatio }}</el-descriptions-item>
            <el-descriptions-item label="损耗费">¥{{ formatMoney(settlement.lossFee) }}</el-descriptions-item>
            <el-descriptions-item label="应退押金">
              <strong>¥{{ formatMoney(settlement.refundDeposit) }}</strong>
            </el-descriptions-item>
          </el-descriptions>
          <p class="muted small">
            应退 = max(押金 − 逾期费 − 损耗费, 0)。全部金额由后端算，页面只展示。
          </p>
        </section>

        <section class="card">
          <h3>损耗与物品去向</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="归还成色">
              <el-tag v-if="returnMeta" :color="returnMeta.color" effect="plain">
                {{ returnMeta.label }}（系数 {{ returnMeta.coef }}）
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="累计损耗 L">
              {{ settlement.lossBefore }} → <strong>{{ settlement.lossAfter }}</strong>
            </el-descriptions-item>
            <el-descriptions-item label="物品处置">
              <el-tag v-if="actionMeta" :color="actionMeta.color" effect="plain">
                {{ actionMeta.label }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
          <p class="muted small">
            w = 租期系数 × 类目系数 × 归还状态系数；L 超过 0.6 需保养、超过 0.85 转检修（检修中不可租）。
          </p>
        </section>
      </div>

      <section class="card flows">
        <h3>资金流水</h3>
        <el-table :data="flowRows" size="small">
          <el-table-column prop="label" label="类型" width="120" />
          <el-table-column label="方向" width="100">
            <template #default="{ row }">
              <el-tag :type="row.direction === 'OUT' ? 'danger' : 'success'" effect="plain" size="small">
                {{ row.direction === 'OUT' ? '支出' : '收取' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="140">
            <template #default="{ row }">¥{{ formatMoney(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="说明" />
        </el-table>
      </section>

      <div class="footer-actions">
        <el-button @click="router.push({ name: 'orders' })">返回我的订单</el-button>
        <el-button plain @click="router.push({ name: 'order-detail', params: { id: settlement.orderId } })">
          查看订单
        </el-button>
        <span class="spacer" />
        <el-button v-if="settlement.canDispute" :loading="acting" @click="onDispute">提异议</el-button>
        <el-button v-if="settlement.canConfirm" type="primary" :loading="acting" @click="onConfirm">
          确认无异议
        </el-button>
      </div>
    </template>
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

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $space-4;
  padding: $space-4;
  margin-bottom: $space-4;

  h2 {
    margin: $space-2 0 $space-1;
    font-size: $font-size-lg;
  }
}

.status-tag {
  margin-top: $space-1;
}

.refund {
  text-align: right;

  strong {
    display: block;
    font-size: $font-size-xl;
    color: $color-primary-deep;
  }
}

.notice {
  margin-bottom: $space-4;
}

.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $space-6;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }

  .card {
    padding: $space-4;
  }

  h3 {
    margin: 0 0 $space-3;
    font-size: $font-size-md;
  }
}

.flows {
  margin-top: $space-6;
  padding: $space-4;

  h3 {
    margin: 0 0 $space-3;
    font-size: $font-size-md;
  }
}

.small {
  font-size: $font-size-xs;
}

.footer-actions {
  display: flex;
  align-items: center;
  gap: $space-3;
  margin-top: $space-6;
}

.spacer {
  flex: 1;
}
</style>
