<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAppeals, verdictAppeal } from '@/api/appeal'

// 管理端没有独立的 format 工具（与用户端刻意不共用代码），这里就地实现三件小事
const formatMoney = (value) => (Number.isFinite(Number(value)) ? Number(value).toFixed(2) : '0.00')
const formatDateTime = (value) => (value ? String(value) : '—')
const resolveImageUrl = (url) => url || ''

const PAGE_SIZE = 10

const APPEAL_STATUS = {
  PENDING: { label: '待裁决', tag: 'warning' },
  ACCEPTED: { label: '支持承租方', tag: 'success' },
  REJECTED: { label: '驳回异议', tag: 'info' }
}

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const statusFilter = ref('PENDING')
const drawerVisible = ref(false)
const current = ref(null)

const drawerTitle = computed(() => (current.value ? `申诉 · ${current.value.orderNo}` : '申诉详情'))

const statusMeta = (status) => APPEAL_STATUS[status] || { label: status, tag: 'info' }

async function load() {
  loading.value = true
  try {
    const page = await listAppeals({
      status: statusFilter.value || undefined,
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
    })
    rows.value = page?.list || []
    total.value = page?.total || 0
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function openDrawer(row) {
  current.value = row
  drawerVisible.value = true
}

/** 列表里直接裁决：先把行设为当前项，再走同一套裁决流程 */
function openVerdict(row, accepted) {
  current.value = row
  runVerdict(accepted)
}

async function runVerdict(accepted) {
  const row = current.value
  const promptText = accepted
    ? '支持承租方：损耗费免除，应退押金按「押金 − 逾期费」重算。请填写裁决说明。'
    : '驳回异议：结算单按原金额结清。请填写裁决说明。'
  const result = await ElMessageBox.prompt(promptText, accepted ? '支持承租方' : '驳回异议', {
    confirmButtonText: '提交裁决',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputPlaceholder: '写清事实依据，会显示给双方',
    inputValidator: (value) => (value && value.trim().length >= 5) || '裁决说明请至少写 5 个字',
  }).catch(() => null)
  if (!result) return

  try {
    await verdictAppeal(row.id, { accepted, verdict: result.value.trim() })
    ElMessage.success(accepted ? '已支持承租方，结算单转「已调整」' : '已驳回异议，结算单按原金额结清')
    drawerVisible.value = false
    await load()
  } catch {
    // 3009（已裁决过）等由 request.js 提示
  }
}

function onStatusChange() {
  pageNum.value = 1
  load()
}

function onPageChange(page) {
  pageNum.value = page
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="toolbar panel">
      <div class="filters">
        <span class="label">状态</span>
        <el-radio-group v-model="statusFilter" @change="onStatusChange">
          <el-radio-button value="PENDING">待裁决</el-radio-button>
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="ACCEPTED">已支持</el-radio-button>
          <el-radio-button value="REJECTED">已驳回</el-radio-button>
        </el-radio-group>
      </div>
      <span class="muted">共 {{ total }} 条</span>
    </div>

    <el-table v-loading="loading" :data="rows" class="panel" size="small">
      <el-table-column prop="orderNo" label="订单号" width="150" />
      <el-table-column prop="itemTitle" label="物品" min-width="140" />
      <el-table-column prop="applicantNickname" label="申诉人" width="100" />
      <el-table-column prop="reason" label="申诉理由" min-width="220" show-overflow-tooltip />
      <el-table-column label="结算单" width="150">
        <template #default="{ row }">
          {{ row.settlementStatusLabel }}<br />
          <span class="muted small">损耗 ¥{{ formatMoney(row.lossFee) }} · 应退 ¥{{ formatMoney(row.refundDeposit) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusMeta(row.status).tag" effect="plain" size="small">
            {{ statusMeta(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="165">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDrawer(row)">查看</el-button>
          <template v-if="row.status === 'PENDING'">
            <el-button link type="success" @click="openVerdict(row, true)">支持</el-button>
            <el-button link type="danger" @click="openVerdict(row, false)">驳回</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="PAGE_SIZE"
        :current-page="pageNum"
        @current-change="onPageChange"
      />
    </div>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="460px">
      <template v-if="current">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="订单号">{{ current.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="物品">{{ current.itemTitle }}</el-descriptions-item>
          <el-descriptions-item label="申诉人">{{ current.applicantNickname }}</el-descriptions-item>
          <el-descriptions-item label="申诉理由">{{ current.reason }}</el-descriptions-item>
          <el-descriptions-item label="结算单">
            {{ current.settleNo }}（{{ current.settlementStatusLabel }}）
          </el-descriptions-item>
          <el-descriptions-item label="损耗费 / 应退">
            ¥{{ formatMoney(current.lossFee) }} / ¥{{ formatMoney(current.refundDeposit) }}
          </el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="statusMeta(current.status).tag" effect="plain" size="small">
              {{ statusMeta(current.status).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="current.verdict" label="裁决说明">{{ current.verdict }}</el-descriptions-item>
          <el-descriptions-item v-if="current.verdictTime" label="裁决时间">
            {{ formatDateTime(current.verdictTime) }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="current.evidenceUrls?.length" class="evidence">
          <p class="label">证据图片</p>
          <div class="evidence-list">
            <el-image
              v-for="url in current.evidenceUrls"
              :key="url"
              :src="resolveImageUrl(url)"
              :preview-src-list="current.evidenceUrls.map(resolveImageUrl)"
              fit="cover"
              class="thumb"
            />
          </div>
        </div>

        <div v-if="current.status === 'PENDING'" class="drawer-actions">
          <el-button type="danger" plain @click="runVerdict(false)">驳回异议</el-button>
          <el-button type="primary" @click="runVerdict(true)">支持承租方</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.page {
  display: flex;
  flex-direction: column;
  gap: $space-4;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $space-3 $space-4;
}

.filters {
  display: flex;
  align-items: center;
  gap: $space-3;
}

.label {
  font-size: $font-size-sm;
  color: $color-text-secondary;
}

.small {
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
}

.evidence {
  margin-top: $space-4;
}

.evidence-list {
  display: flex;
  gap: $space-2;
  flex-wrap: wrap;
}

.thumb {
  width: 88px;
  height: 88px;
  border-radius: 6px;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: $space-3;
  margin-top: $space-6;
}
</style>
