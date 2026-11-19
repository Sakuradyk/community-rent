<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, confirmOrder, getOrder, pickupOrder } from '@/api/order'
import { useUserStore } from '@/stores/user'
import { ORDER_STATUS, statusOf } from '@/utils/constants'
import { formatDate, formatDateTime, formatMoney, resolveImageUrl } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const order = ref(null)
const loading = ref(true)
const acting = ref(false)
const errorText = ref('')

const statusMeta = (status) => statusOf(ORDER_STATUS, status)

// 身份判断用字符串比较：后端把 Long 序列化成字符串，直接 === 数字永远不成立
const isOwner = computed(() => String(order.value?.owner?.id) === String(userStore.profile?.id))
const isRenter = computed(() => String(order.value?.renter?.id) === String(userStore.profile?.id))
const canConfirm = computed(() => isOwner.value && order.value?.status === 'PENDING')
const canPickup = computed(() => isOwner.value && order.value?.status === 'CONFIRMED')
const canCancel = computed(
  () => (isOwner.value || isRenter.value) && ['PENDING', 'CONFIRMED'].includes(order.value?.status),
)

async function load() {
  loading.value = true
  try {
    order.value = await getOrder(route.params.id)
    errorText.value = ''
  } catch (e) {
    errorText.value = e?.message || '订单加载失败'
  } finally {
    loading.value = false
  }
}

/** 三个动作的公共壳：按钮忙碌 → 调接口 → 提示 → 重新拉详情（状态与可点按钮都跟着变） */
async function runAction(action, successText) {
  acting.value = true
  try {
    await action()
    ElMessage.success(successText)
    await load()
  } catch {
    // 失败提示由 request.js 统一弹出（3001/3008 等），这里只负责恢复按钮
  } finally {
    acting.value = false
  }
}

const onConfirm = () => runAction(() => confirmOrder(order.value.id), '已确认出租，等待对方取用')
const onPickup = () => runAction(() => pickupOrder(order.value.id), '已标记取用，物品进入租出中')

async function onCancel() {
  const result = await ElMessageBox.prompt('取消后该时段会立即释放给其他人，可填写原因（留空则记「用户主动取消」）', '取消订单', {
    confirmButtonText: '确认取消',
    cancelButtonText: '再想想',
    inputPlaceholder: '例如：临时不需要了',
  }).catch(() => null)
  if (!result) return
  await runAction(() => cancelOrder(order.value.id, { reason: result.value || undefined }), '订单已取消，占用已释放')
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <el-page-header class="back" @back="router.back()">
      <template #content><span class="title">订单详情</span></template>
    </el-page-header>

    <el-skeleton v-if="loading" :rows="6" animated />
    <el-empty v-else-if="errorText || !order" :description="errorText || '订单不存在'" />

    <template v-else>
      <section class="card head">
        <div class="head-main">
          <el-tag :color="statusMeta(order.status).color" effect="dark" class="status-tag">
            {{ order.statusLabel }}
          </el-tag>
          <div>
            <h2>{{ order.itemTitle }}</h2>
            <p class="muted">订单号 {{ order.orderNo }} · 下单于 {{ formatDateTime(order.createdAt) }}</p>
          </div>
        </div>
        <img class="cover" :src="resolveImageUrl(order.itemCoverUrl)" :alt="order.itemTitle" />
      </section>

      <div class="grid">
        <section class="card">
          <h3>租期与费用</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="租期">
              {{ formatDate(order.startDate) }} → {{ formatDate(order.endDate) }}
              （{{ order.rentDays }} 天）
            </el-descriptions-item>
            <el-descriptions-item label="日租金">¥{{ formatMoney(order.dailyPrice) }}</el-descriptions-item>
            <el-descriptions-item label="租金">¥{{ formatMoney(order.rentAmount) }}</el-descriptions-item>
            <el-descriptions-item label="押金">¥{{ formatMoney(order.depositAmount) }}</el-descriptions-item>
            <el-descriptions-item label="合计">¥{{ formatMoney(order.totalAmount) }}</el-descriptions-item>
            <el-descriptions-item label="逾期费率">{{ order.overdueRate }} 倍 / 天</el-descriptions-item>
          </el-descriptions>
          <p class="muted small">费用为下单时的价格快照，物品改价不影响已下的订单。</p>
        </section>

        <section class="card">
          <h3>双方与备注</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="出租方">{{ order.owner?.nickname }}</el-descriptions-item>
            <el-descriptions-item label="承租方">{{ order.renter?.nickname }}</el-descriptions-item>
            <el-descriptions-item label="类目 / 小区">
              {{ order.categoryName }} · {{ order.communityName }}
            </el-descriptions-item>
            <el-descriptions-item label="备注">
              <span v-if="order.remark">{{ order.remark }}</span>
              <span v-else class="muted">无</span>
            </el-descriptions-item>
          </el-descriptions>
          <p class="muted small">
            状态流转：出租方「确认出租 → 标记取用」，双方都可在取用前取消；取用后只能走归还（切片 5 开放，含结算冷静期）。
          </p>
        </section>
      </div>

      <div class="footer-actions">
        <el-button @click="router.push({ name: 'orders' })">返回我的订单</el-button>
        <el-button
          type="primary"
          plain
          @click="router.push({ name: 'item-detail', params: { id: order.itemId } })"
        >
          查看物品
        </el-button>

        <span class="spacer" />

        <el-button v-if="canCancel" :loading="acting" @click="onCancel">取消订单</el-button>
        <el-button v-if="canConfirm" type="primary" :loading="acting" @click="onConfirm">确认出租</el-button>
        <el-button v-if="canPickup" type="primary" :loading="acting" @click="onPickup">标记已取用</el-button>
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

.head-main {
  display: flex;
  gap: $space-4;
  align-items: flex-start;
}

.status-tag {
  margin-top: $space-1;
}

.cover {
  width: 120px;
  height: 120px;
  object-fit: cover;
  border-radius: $radius-base;
  background: $color-surface-sunken;
}

.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $space-6;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }

  h3 {
    margin: 0 0 $space-3;
    font-size: $font-size-md;
  }

  .card {
    padding: $space-4;
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
