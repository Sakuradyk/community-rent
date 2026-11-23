<template>
  <div class="page">
    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-head">
          <span>账务对账</span>
          <el-button size="small" :loading="reconcileLoading" @click="loadReconcile">重新对账</el-button>
        </div>
      </template>
      <div class="recon">
        <div class="recon-item">
          <div class="recon-value">
            <el-tag v-if="reconcile" :type="reconcile.balanced ? 'success' : 'danger'" effect="dark">
              {{ reconcile.balanced ? '守恒' : '不守恒' }}
            </el-tag>
            <span v-else class="muted">—</span>
          </div>
          <div class="muted small">全站余额之和应为 0</div>
        </div>
        <div class="recon-item">
          <div class="recon-value">¥{{ money(reconcile?.balanceSum) }}</div>
          <div class="muted small">余额总和</div>
        </div>
        <div class="recon-item">
          <div class="recon-value">¥{{ money(reconcile?.externalCash) }}</div>
          <div class="muted small">外部账户（充值流入合计，正常为负）</div>
        </div>
        <div class="recon-item">
          <div class="recon-value">¥{{ money(reconcile?.userCash) }}</div>
          <div class="muted small">用户可用余额合计</div>
        </div>
        <div class="recon-item">
          <div class="recon-value">¥{{ money(reconcile?.userFrozen) }}</div>
          <div class="muted small">用户冻结余额合计</div>
        </div>
        <div class="recon-item">
          <div class="recon-value">{{ reconcile?.entryCount ?? 0 }}</div>
          <div class="muted small">分录总数</div>
        </div>
      </div>
      <el-alert
        v-if="reconcile && reconcile.issues && reconcile.issues.length"
        type="error"
        :closable="false"
        show-icon
        title="对账发现异常"
        style="margin-top: 12px"
      >
        <div v-for="(issue, i) in reconcile.issues" :key="i">{{ issue }}</div>
      </el-alert>
    </el-card>

    <el-card shadow="never" class="card">
      <el-tabs v-model="tab">
        <el-tab-pane label="用户账户" name="accounts">
          <div class="filter">
            <el-input
              v-model="accountQuery.keyword"
              placeholder="按昵称 / 手机号搜索"
              clearable
              style="width: 240px"
              @keyup.enter="searchAccounts"
            />
            <el-button type="primary" @click="searchAccounts">查询</el-button>
          </div>

          <el-table v-loading="accountLoading" :data="accounts" empty-text="没有账户" size="default">
            <el-table-column label="用户" min-width="160">
              <template #default="{ row }">
                <span>{{ row.nickname || '未设置昵称' }}</span>
                <span class="muted small"> #{{ row.ownerId }}</span>
              </template>
            </el-table-column>
            <el-table-column label="可用余额" width="130" align="right">
              <template #default="{ row }">¥{{ money(row.cashBalance) }}</template>
            </el-table-column>
            <el-table-column label="冻结中" width="130" align="right">
              <template #default="{ row }">¥{{ money(row.frozenBalance) }}</template>
            </el-table-column>
            <el-table-column label="总额" width="130" align="right">
              <template #default="{ row }">¥{{ money(row.totalBalance) }}</template>
            </el-table-column>
            <el-table-column label="分录数" width="90" align="right" prop="entryCount" />
            <el-table-column label="更新时间" width="170">
              <template #default="{ row }">{{ row.updateTime || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openAdjust(row)">人工调账</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="accountQuery.pageNum"
              :page-size="accountQuery.pageSize"
              :total="accountTotal"
              layout="total, prev, pager, next"
              background
              @current-change="loadAccounts"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="全站分录" name="entries">
          <div class="filter">
            <el-input
              v-model="entryQuery.bizType"
              placeholder="业务类型（ORDER_FREEZE / SETTLE / RECHARGE …）"
              clearable
              style="width: 320px"
              @keyup.enter="searchEntries"
            />
            <el-button type="primary" @click="searchEntries">查询</el-button>
          </div>

          <el-table v-loading="entryLoading" :data="entries" empty-text="没有分录" size="default">
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ row.createdAt }}</template>
            </el-table-column>
            <el-table-column label="业务" width="150">
              <template #default="{ row }">
                <span class="mono small">{{ row.bizType }}#{{ row.bizId ?? '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="动作" width="110">
              <template #default="{ row }">
                <el-tag size="small">{{ row.actionLabel || row.action }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="出账 → 入账" min-width="240">
              <template #default="{ row }">
                <span>{{ row.fromName }}（{{ row.fromBucket }}）</span>
                <span class="muted"> → </span>
                <span>{{ row.toName }}（{{ row.toBucket }}）</span>
              </template>
            </el-table-column>
            <el-table-column label="金额" width="120" align="right">
              <template #default="{ row }">¥{{ money(row.amount) }}</template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="entryQuery.pageNum"
              :page-size="entryQuery.pageSize"
              :total="entryTotal"
              layout="total, prev, pager, next"
              background
              @current-change="loadEntries"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="adjustVisible" title="人工调账" width="420px">
      <el-form label-width="88px">
        <el-form-item label="用户">
          <span>{{ adjustingRow?.nickname || '—' }} #{{ adjustingRow?.ownerId }}</span>
        </el-form-item>
        <el-form-item label="调整金额">
          <el-input-number v-model="adjustForm.amount" :precision="2" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="adjustForm.remark" type="textarea" :rows="3" :maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <div class="muted small">正数给用户补款，负数从用户扣款；只有演示环境才用，正式流程一律由订单驱动。</div>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjustLoading" @click="submitAdjust">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adjustAccount, getReconcile, listAccountEntries, listAccounts } from '@/api/account'

const tab = ref('accounts')

const reconcile = ref(null)
const reconcileLoading = ref(false)

const accounts = ref([])
const accountTotal = ref(0)
const accountLoading = ref(false)
const accountQuery = reactive({ keyword: '', pageNum: 1, pageSize: 10 })

const entries = ref([])
const entryTotal = ref(0)
const entryLoading = ref(false)
const entryQuery = reactive({ bizType: '', pageNum: 1, pageSize: 10 })

const adjustVisible = ref(false)
const adjustLoading = ref(false)
const adjustingRow = ref(null)
const adjustForm = reactive({ amount: 0, remark: '' })

function money(v) {
  return Number(v || 0).toFixed(2)
}

async function loadReconcile() {
  reconcileLoading.value = true
  try {
    reconcile.value = await getReconcile()
  } finally {
    reconcileLoading.value = false
  }
}

async function loadAccounts() {
  accountLoading.value = true
  try {
    const data = await listAccounts({
      keyword: accountQuery.keyword || undefined,
      pageNum: accountQuery.pageNum,
      pageSize: accountQuery.pageSize
    })
    accounts.value = data?.list || []
    accountTotal.value = data?.total || 0
  } finally {
    accountLoading.value = false
  }
}

function searchAccounts() {
  accountQuery.pageNum = 1
  loadAccounts()
}

async function loadEntries() {
  entryLoading.value = true
  try {
    const data = await listAccountEntries({
      bizType: entryQuery.bizType || undefined,
      pageNum: entryQuery.pageNum,
      pageSize: entryQuery.pageSize
    })
    entries.value = data?.list || []
    entryTotal.value = data?.total || 0
  } finally {
    entryLoading.value = false
  }
}

function searchEntries() {
  entryQuery.pageNum = 1
  loadEntries()
}

function openAdjust(row) {
  adjustingRow.value = row
  adjustForm.amount = 0
  adjustForm.remark = ''
  adjustVisible.value = true
}

async function submitAdjust() {
  if (!adjustForm.amount) {
    ElMessage.warning('调整金额不能为 0')
    return
  }
  if (!adjustForm.remark.trim()) {
    ElMessage.warning('必须填写调整原因')
    return
  }
  adjustLoading.value = true
  try {
    await adjustAccount(adjustingRow.value.ownerId, {
      amount: adjustForm.amount,
      remark: adjustForm.remark.trim()
    })
    adjustVisible.value = false
    ElMessage.success('调账完成')
    await Promise.all([loadAccounts(), loadReconcile()])
  } finally {
    adjustLoading.value = false
  }
}

onMounted(() => {
  loadReconcile()
  loadAccounts()
  loadEntries()
})
</script>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.page {
  display: flex;
  flex-direction: column;
  gap: $space-3;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.recon {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: $space-3;
}

.recon-item {
  padding: $space-2;
  border: 1px solid $color-border;
  border-radius: 6px;
  background: $color-surface-sunken;
}

.recon-value {
  font-size: 18px;
  font-weight: 600;
  color: $color-text;
}

.filter {
  display: flex;
  gap: $space-2;
  margin-bottom: $space-3;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: $space-3;
}

.mono {
  font-family: Consolas, Monaco, monospace;
}
</style>
