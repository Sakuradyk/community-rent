<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listAppeals, verdictAppeal } from '@/api/appeal'

// 管理端没有独立的 format 工具（与用户端刻意不共用代码），这里就地实现三件小事
const formatMoney = (value) => (Number.isFinite(Number(value)) ? Number(value).toFixed(2) : '0.00')
const formatDateTime = (value) => (value ? String(value) : '—')
const resolveImageUrl = (url) => url || ''

const PAGE_SIZE = 10

const APPEAL_STATUS = {
  PENDING: { label: '待裁决', tag: 'warning' },
  ACCEPTED: { label: '已裁决', tag: 'success' },
  REJECTED: { label: '已驳回', tag: 'info' },
}

/**
 * 四档裁定（与后端 VerdictType 一一对应）。
 * 为什么不用「通过/驳回」两个按钮：申诉人可能是承租方也可能是出租方，
 * 同一个「通过」对双方是相反的意思——把损耗费退给承租方，等于让出租方自己承担损失。
 */
const VERDICT_TYPE = {
  RENTER_WIN: { label: '支持承租方', tag: 'success', effect: '损耗费归零，应退 = 押金 − 逾期费' },
  OWNER_WIN: { label: '支持出租方', tag: 'warning', effect: '按原损耗率恢复损耗费，应退相应减少' },
  PARTIAL: { label: '部分支持', tag: 'primary', effect: '由你给出损耗费金额，应退按此重算' },
  REJECTED: { label: '驳回异议', tag: 'info', effect: '金额不动，结算单按原样结清' },
}

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const statusFilter = ref('PENDING')
const drawerVisible = ref(false)
const current = ref(null)

const verdictVisible = ref(false)
const verdictSubmitting = ref(false)
const verdictForm = ref({ verdictType: 'RENTER_WIN', lossFeeOverride: null, creditAdjust: null, verdict: '' })

const drawerTitle = computed(() => (current.value ? `申诉 · ${current.value.orderNo}` : '申诉详情'))
const originalLossFee = computed(() => Number(current.value?.lossFee ?? 0))
const statusMeta = (status) => APPEAL_STATUS[status] || { label: status, tag: 'info' }
const verdictMeta = (type) => VERDICT_TYPE[type] || { label: type || '—', tag: 'info', effect: '' }

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

/** 打开裁决对话框（列表与抽屉共用一个入口，避免两套 UI 逻辑漂移） */
function openVerdict(row) {
  current.value = row
  verdictForm.value = { verdictType: 'RENTER_WIN', lossFeeOverride: null, creditAdjust: null, verdict: '' }
  drawerVisible.value = false
  verdictVisible.value = true
}

async function submitVerdict() {
  const form = verdictForm.value
  const verdict = (form.verdict || '').trim()
  if (verdict.length < 5) {
    ElMessage.warning('裁决说明请至少写 5 个字')
    return
  }
  if (form.verdictType === 'PARTIAL') {
    const override = Number(form.lossFeeOverride)
    if (!Number.isFinite(override) || override < 0) {
      ElMessage.warning('部分支持必须填写损耗费金额')
      return
    }
    if (override > originalLossFee.value) {
      ElMessage.warning('部分支持的金额不能高于原损耗费')
      return
    }
  }

  verdictSubmitting.value = true
  try {
    await verdictAppeal(current.value.id, {
      verdictType: form.verdictType,
      lossFeeOverride: form.verdictType === 'PARTIAL' ? Number(form.lossFeeOverride) : null,
      creditAdjust: form.creditAdjust ?? null,
      verdict,
    })
    ElMessage.success(`已裁定：${verdictMeta(form.verdictType).label}`)
    verdictVisible.value = false
    await load()
  } catch {
    // 3009（已裁决过）、2001（入参不合法）由 request.js 统一提示
  } finally {
    verdictSubmitting.value = false
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
          <el-radio-button value="ACCEPTED">已裁决</el-radio-button>
          <el-radio-button value="REJECTED">已驳回</el-radio-button>
        </el-radio-group>
      </div>
      <span class="muted">共 {{ total }} 条</span>
    </div>

    <el-table v-loading="loading" :data="rows" class="panel" size="small">
      <el-table-column prop="orderNo" label="订单号" width="150" />
      <el-table-column prop="itemTitle" label="物品" min-width="130" />
      <el-table-column prop="applicantNickname" label="申诉人" width="90" />
      <el-table-column prop="reason" label="申诉理由" min-width="200" show-overflow-tooltip />
      <el-table-column label="结算单" width="160">
        <template #default="{ row }">
          {{ row.settlementStatusLabel }}<br />
          <span class="muted small">损耗 ¥{{ formatMoney(row.lossFee) }} · 应退 ¥{{ formatMoney(row.refundDeposit) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="裁定" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.verdictType" :type="verdictMeta(row.verdictType).tag" effect="plain" size="small">
            {{ row.verdictTypeLabel || verdictMeta(row.verdictType).label }}
          </el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMeta(row.status).tag" effect="plain" size="small">
            {{ statusMeta(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDrawer(row)">查看</el-button>
          <el-button v-if="row.status === 'PENDING'" link type="warning" @click="openVerdict(row)">裁决</el-button>
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
          <el-descriptions-item v-if="current.verdictType" label="裁定">
            <el-tag :type="verdictMeta(current.verdictType).tag" effect="plain" size="small">
              {{ current.verdictTypeLabel || verdictMeta(current.verdictType).label }}
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
          <el-button type="primary" @click="openVerdict(current)">作出裁决</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="verdictVisible" title="裁决申诉" width="560px" :close-on-click-modal="false">
      <template v-if="current">
        <el-alert type="info" :closable="false" show-icon class="tip">
          申诉人：{{ current.applicantNickname }} · 原损耗费 ¥{{ formatMoney(current.lossFee) }} ·
          原应退 ¥{{ formatMoney(current.refundDeposit) }}
          <template #title>选哪一种裁定，决定钱怎么动</template>
        </el-alert>

        <el-form label-width="96px" label-position="right">
          <el-form-item label="裁定结果">
            <el-radio-group v-model="verdictForm.verdictType" class="verdict-group">
              <el-radio
                v-for="(meta, type) in VERDICT_TYPE"
                :key="type"
                :value="type"
                border
                class="verdict-radio"
              >
                {{ meta.label }}
              </el-radio>
            </el-radio-group>
            <p class="hint">{{ verdictMeta(verdictForm.verdictType).effect }}</p>
          </el-form-item>

          <el-form-item v-if="verdictForm.verdictType === 'PARTIAL'" label="损耗费金额">
            <el-input-number
              v-model="verdictForm.lossFeeOverride"
              :min="0"
              :max="originalLossFee"
              :step="0.01"
              :precision="2"
              controls-position="right"
            />
            <span class="hint inline">区间 0 ~ {{ formatMoney(originalLossFee) }}</span>
          </el-form-item>

          <el-form-item label="信誉调整">
            <el-input-number
              v-model="verdictForm.creditAdjust"
              :min="-50"
              :max="50"
              :step="1"
              controls-position="right"
            />
            <span class="hint inline">可选，作用于承租方，留空表示不调整</span>
          </el-form-item>

          <el-form-item label="裁决说明" required>
            <el-input
              v-model="verdictForm.verdict"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              placeholder="写清事实依据，双方都能看到（至少 5 个字）"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="verdictVisible = false">取消</el-button>
        <el-button type="primary" :loading="verdictSubmitting" @click="submitVerdict">提交裁决</el-button>
      </template>
    </el-dialog>
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

.tip {
  margin-bottom: $space-4;
}

.verdict-group {
  display: flex;
  flex-wrap: wrap;
  gap: $space-2;
}

.verdict-radio {
  margin-right: 0;
}

.hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: $color-text-secondary;
  line-height: 1.5;
}

.hint.inline {
  margin-left: $space-3;
}
</style>
