<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteItem, listMyItems, shelfItem } from '@/api/item'
import { ITEM_STATUS, LIMITS, statusOf } from '@/utils/constants'
import { formatMoney, formatDateTime, resolveImageUrl } from '@/utils/format'

const router = useRouter()

/** 损耗条配色与详情页一致：<0.6 正常、≥0.6 提示保养、≥0.85 转检修 */
function lossColorOf(value) {
  const v = Number(value || 0)
  if (v >= 0.85) return '#b91c1c'
  if (v >= 0.6) return '#b45309'
  return '#15803d'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const acting = ref(null)

const query = reactive({
  status: '',
  pageNum: 1,
  pageSize: LIMITS.PAGE_SIZE
})

const statusTabs = [
  { value: '', label: '全部' },
  ...Object.entries(ITEM_STATUS).map(([value, meta]) => ({ value, label: meta.label }))
]

async function load() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.status) params.status = query.status
    const data = await listMyItems(params)
    list.value = data.list || []
    total.value = data.total || 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function changeStatus() {
  query.pageNum = 1
  load()
}

async function toggleShelf(item) {
  const onShelf = item.status !== 'ON_SHELF'
  acting.value = item.id
  try {
    await shelfItem(item.id, onShelf)
    ElMessage.success(onShelf ? '已下架' : '已上架')
    load()
  } catch (e) {
    // 有进行中订单时后端返回 3003，提示已由 request.js 弹出
  } finally {
    acting.value = null
  }
}

async function remove(item) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${item.title}」吗？删除后不再展示，已有订单记录会保留。`,
      '删除物品',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  acting.value = item.id
  try {
    await deleteItem(item.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    // 提示已由 request.js 弹出
  } finally {
    acting.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <header class="page-head">
      <div>
        <h1>我的物品</h1>
        <p class="muted">审核通过后才会出现在「逛物品」里；被驳回的物品按审核意见改完可重新提交。</p>
      </div>
      <el-button type="primary" @click="router.push({ name: 'item-publish' })">发布新物品</el-button>
    </header>

    <el-radio-group v-model="query.status" class="tabs" @change="changeStatus">
      <el-radio-button v-for="tab in statusTabs" :key="tab.value" :value="tab.value">
        {{ tab.label }}
      </el-radio-button>
    </el-radio-group>

    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && list.length === 0" description="这里还没有物品">
        <el-button type="primary" @click="router.push({ name: 'item-publish' })">去发布第一件</el-button>
      </el-empty>

      <article v-for="item in list" :key="item.id" class="row">
        <div class="thumb">
          <img v-if="item.coverUrl" :src="resolveImageUrl(item.coverUrl)" :alt="item.title" />
          <div v-else class="thumb-fallback">无图</div>
        </div>

        <div class="main">
          <div class="title-row">
            <router-link class="title" :to="{ name: 'item-detail', params: { id: item.id } }">
              {{ item.title }}
            </router-link>
            <el-tag :color="statusOf(ITEM_STATUS, item.status).color" effect="plain" style="border: none">
              {{ item.statusLabel || statusOf(ITEM_STATUS, item.status).label }}
            </el-tag>
          </div>
          <p class="meta">
            {{ item.categoryName }} · {{ item.communityName }} · {{ item.imageCount }} 张图 · 已租
            {{ item.rentCount }} 次
          </p>
          <p class="loss-line">
            <span class="muted">累计损耗</span>
            <el-progress
              :percentage="Math.min(100, Math.round(Number(item.lossValue || 0) * 10000) / 100)"
              :stroke-width="6"
              :color="lossColorOf(item.lossValue)"
              :show-text="false"
              class="loss-bar"
            />
            <span class="loss-num">{{ Number(item.lossValue || 0).toFixed(4) }}</span>
            <el-tag v-if="Number(item.lossValue || 0) >= 0.85" size="small" color="#b91c1c" effect="plain" style="border:none">该检修了</el-tag>
            <el-tag v-else-if="Number(item.lossValue || 0) >= 0.6" size="small" color="#b45309" effect="plain" style="border:none">该保养了</el-tag>
          </p>
          <p class="price">
            ¥{{ formatMoney(item.dailyPrice) }}/天 <span class="muted">押金 ¥{{ formatMoney(item.deposit) }}</span>
          </p>
          <el-alert
            v-if="item.auditRemark"
            :type="item.status === 'REJECTED' ? 'error' : 'info'"
            :closable="false"
            show-icon
            :title="`审核意见：${item.auditRemark}`"
            class="remark"
          />
          <p class="muted time">发布于 {{ formatDateTime(item.createdAt) }}</p>
        </div>

        <div class="ops">
          <el-button
            size="small"
            :disabled="!['PENDING', 'OFF_SHELF', 'REJECTED'].includes(item.status)"
            @click="router.push({ name: 'item-publish', query: { edit: item.id } })"
          >
            编辑
          </el-button>
          <el-button
            size="small"
            :loading="acting === item.id"
            :disabled="['PENDING', 'REJECTED', 'RENTED', 'MAINTAINING'].includes(item.status)"
            @click="toggleShelf(item)"
          >
            {{ item.status === 'ON_SHELF' ? '下架' : '上架' }}
          </el-button>
          <el-button size="small" type="danger" plain :loading="acting === item.id" @click="remove(item)">
            删除
          </el-button>
        </div>
      </article>
    </div>

    <div v-if="total > query.pageSize" class="pager">
      <el-pagination
        background
        layout="prev, pager, next"
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        :total="total"
        @current-change="(page) => { query.pageNum = page; load() }"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;
.loss-line {
  display: flex;
  align-items: center;
  gap: $space-2;
  margin: $space-1 0 0;
  font-size: $font-size-xs;
}

.loss-bar {
  width: 90px;
  flex: 0 0 90px;
}

.loss-num {
  color: $color-text;
  font-variant-numeric: tabular-nums;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $space-4;
  margin-bottom: $space-4;

  h1 {
    margin: 0 0 $space-1;
    font-size: $font-size-xl;
  }

  p {
    margin: 0;
    font-size: $font-size-sm;
  }
}

.tabs {
  margin-bottom: $space-4;
}

.list {
  min-height: 200px;
}

.row {
  display: flex;
  gap: $space-4;
  padding: $space-4;
  background: $color-surface;
  border: 1px solid $color-border;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;

  & + .row {
    margin-top: $space-3;
  }
}

.thumb {
  width: 112px;
  height: 84px;
  flex: 0 0 auto;
  border-radius: $radius-sm;
  overflow: hidden;
  background: $color-surface-sunken;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}

.thumb-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: $color-text-placeholder;
  font-size: $font-size-xs;
}

.main {
  flex: 1 1 auto;
  min-width: 0;
}

.title-row {
  display: flex;
  align-items: center;
  gap: $space-2;
}

.title {
  color: $color-text;
  font-size: $font-size-md;
  font-weight: 600;

  &:hover {
    color: $color-primary-deep;
  }
}

.meta {
  margin: $space-1 0;
  font-size: $font-size-xs;
  color: $color-text-secondary;
}

.price {
  margin: 0;
  color: $color-primary-deep;
  font-weight: 600;

  .muted {
    font-weight: 400;
    font-size: $font-size-xs;
  }
}

.remark {
  margin: $space-2 0 0;
}

.time {
  margin: $space-1 0 0;
  font-size: $font-size-xs;
}

.ops {
  display: flex;
  flex-direction: column;
  gap: $space-2;
  flex: 0 0 auto;

  .el-button + .el-button {
    margin-left: 0;
  }
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: $space-6;
}
</style>
