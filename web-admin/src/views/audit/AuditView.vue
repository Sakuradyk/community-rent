<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditItem, listAdminItems } from '@/api/item'
import { ITEM_STATUS, statusOf } from '@/utils/constants'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const acting = ref(null)
const detail = ref(null)
const detailVisible = ref(false)

const query = reactive({
  status: 'PENDING',
  keyword: '',
  pageNum: 1,
  pageSize: 10
})

const statusTabs = [
  { value: 'PENDING', label: '待审核' },
  { value: '', label: '全部' },
  { value: 'ON_SHELF', label: '已上架' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'OFF_SHELF', label: '已下架' }
]

async function load() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.status) params.status = query.status
    if (query.keyword) params.keyword = query.keyword
    const data = await listAdminItems(params)
    rows.value = data.list || []
    total.value = data.total || 0
  } catch (e) {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

async function approve(row) {
  try {
    await ElMessageBox.confirm(
      `通过后「${row.title}」会立刻出现在用户端的物品列表里，确定通过吗？`,
      '审核通过',
      { type: 'success', confirmButtonText: '通过并上架', cancelButtonText: '再看看' }
    )
  } catch (e) {
    return
  }
  await submitAudit(row, { approved: true })
}

async function reject(row) {
  let remark = ''
  try {
    const result = await ElMessageBox.prompt('驳回必须写清原因，用户会看到这句话。', '驳回物品', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '例如：图片看不清实物成色，请重新拍摄',
      inputValidator: (value) => (value && value.trim().length > 0 ? true : '请填写驳回原因')
    })
    remark = result.value.trim()
  } catch (e) {
    return
  }
  await submitAudit(row, { approved: false, remark })
}

async function submitAudit(row, payload) {
  acting.value = row.id
  try {
    await auditItem(row.id, payload)
    ElMessage.success(payload.approved ? '已通过并上架' : '已驳回')
    load()
  } catch (e) {
    // 非 PENDING（被别的管理员审过 / 用户已改）→ 3003，提示由 request.js 弹出
  } finally {
    acting.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="audit-page">
    <header class="page-head">
      <div>
        <h1>物品审核</h1>
        <p class="muted">默认只看待审核；通过即上架，驳回必须写原因（用户端会原样展示）。</p>
      </div>
      <el-input
        v-model="query.keyword"
        placeholder="搜物品名称"
        clearable
        class="w-220"
        @keyup.enter="search"
      >
        <template #append>
          <el-button @click="search">搜索</el-button>
        </template>
      </el-input>
    </header>

    <el-radio-group v-model="query.status" class="tabs" @change="search">
      <el-radio-button v-for="tab in statusTabs" :key="tab.value" :value="tab.value">
        {{ tab.label }}
      </el-radio-button>
    </el-radio-group>

    <el-table v-loading="loading" :data="rows" border stripe class="table">
      <el-table-column label="封面" width="96">
        <template #default="{ row }">
          <img v-if="row.coverUrl" :src="row.coverUrl" class="cover" :alt="row.title" />
          <span v-else class="muted">无图</span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="类目 / 小区" min-width="150">
        <template #default="{ row }">
          <div>{{ row.categoryName }}</div>
          <div class="muted small">{{ row.communityName }}</div>
        </template>
      </el-table-column>
      <el-table-column label="日租 / 押金" width="140">
        <template #default="{ row }">
          <div>¥{{ row.dailyPrice }}/天</div>
          <div class="muted small">押金 ¥{{ row.deposit }}</div>
        </template>
      </el-table-column>
      <el-table-column label="发布者" min-width="150">
        <template #default="{ row }">
          <div>{{ row.ownerNickname }}</div>
          <div class="muted small">{{ row.ownerPhone }}</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :color="statusOf(ITEM_STATUS, row.status).color" effect="plain" style="border: none">
            {{ row.statusLabel || statusOf(ITEM_STATUS, row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDetail(row)">详情</el-button>
          <el-button
            size="small"
            type="success"
            :loading="acting === row.id"
            :disabled="row.status !== 'PENDING'"
            @click="approve(row)"
          >
            通过
          </el-button>
          <el-button
            size="small"
            type="danger"
            plain
            :loading="acting === row.id"
            :disabled="row.status !== 'PENDING'"
            @click="reject(row)"
          >
            驳回
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="没有符合条件的物品" />
      </template>
    </el-table>

    <div v-if="total > query.pageSize" class="pager">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        :total="total"
        @current-change="(page) => { query.pageNum = page; load() }"
      />
    </div>

    <el-drawer v-model="detailVisible" title="物品详情" size="440px">
      <template v-if="detail">
        <h3 class="drawer-title">{{ detail.title }}</h3>
        <p class="muted small">
          {{ detail.categoryName }} · {{ detail.communityName }} · 发布于 {{ detail.createdAt }}
        </p>
        <div class="drawer-images">
          <img v-for="img in detail.images" :key="img.id" :src="img.url" :alt="detail.title" />
        </div>
        <el-descriptions :column="1" border class="drawer-facts">
          <el-descriptions-item label="日租金">¥{{ detail.dailyPrice }}</el-descriptions-item>
          <el-descriptions-item label="押金">¥{{ detail.deposit }}</el-descriptions-item>
          <el-descriptions-item label="逾期费率">{{ detail.overdueRate }} 倍/天</el-descriptions-item>
          <el-descriptions-item label="发布者">
            {{ detail.ownerNickname }}（{{ detail.ownerPhone }}）
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            {{ detail.statusLabel || statusOf(ITEM_STATUS, detail.status).label }}
          </el-descriptions-item>
          <el-descriptions-item label="描述">
            {{ detail.description || '未填写' }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.audit-page {
  padding: $space-4;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $space-4;
  margin-bottom: $space-4;

  h1 {
    margin: 0 0 4px;
    font-size: 18px;
  }

  p {
    margin: 0;
    font-size: $font-size-xs;
  }
}

.w-220 {
  width: 220px;
}

.tabs {
  margin-bottom: $space-3;
}

.table {
  width: 100%;
}

.cover {
  width: 64px;
  height: 48px;
  object-fit: cover;
  border-radius: 4px;
  display: block;
}

.small {
  font-size: $font-size-xs;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: $space-3;
}

.drawer-title {
  margin: 0 0 4px;
  font-size: 16px;
}

.drawer-images {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $space-2;
  margin: $space-3 0;

  img {
    width: 100%;
    aspect-ratio: 1;
    object-fit: cover;
    border-radius: 4px;
  }
}

.drawer-facts {
  margin-top: $space-2;
}
</style>
