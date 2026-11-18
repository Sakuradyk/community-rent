<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import OccupancyCalendar from '@/components/OccupancyCalendar.vue'
import { getItem } from '@/api/item'
import { useUserStore } from '@/stores/user'
import { ITEM_STATUS, MAINTENANCE_STATE, statusOf } from '@/utils/constants'
import { formatDateTime, formatMoney, resolveImageUrl } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const notFound = ref(false)
const item = ref(null)
const activeImage = ref(0)

const images = computed(() => (item.value?.images || []).map((img) => resolveImageUrl(img.url)))
const mainImage = computed(() => images.value[activeImage.value] || '')
const status = computed(() => statusOf(ITEM_STATUS, item.value?.status))
const maintenance = computed(() => statusOf(MAINTENANCE_STATE, item.value?.maintenanceState))
const isOwner = computed(() => Boolean(item.value) && String(item.value.owner?.id) === String(userStore.profile?.id))

async function load() {
  loading.value = true
  notFound.value = false
  try {
    item.value = await getItem(route.params.id)
  } catch (e) {
    // 3004（不存在 / 待审核对他人不可见）会走到这里；提示已由 request.js 统一弹出
    notFound.value = true
    item.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-page-header class="back" @back="router.back()">
      <template #content><span class="back-title">物品详情</span></template>
    </el-page-header>

    <el-empty v-if="notFound" description="物品不存在，或还在审核中（仅发布者可见）">
      <el-button type="primary" @click="router.push({ name: 'items' })">去看看别的物品</el-button>
    </el-empty>

    <template v-else-if="item">
      <div class="detail">
        <section class="gallery">
          <div class="main-image">
            <img v-if="mainImage" :src="mainImage" :alt="item.title" />
            <div v-else class="image-fallback">暂无图片</div>
          </div>
          <div v-if="images.length > 1" class="thumbs">
            <button
              v-for="(url, index) in images"
              :key="url"
              class="thumb"
              :class="{ active: index === activeImage }"
              type="button"
              @click="activeImage = index"
            >
              <img :src="url" :alt="`${item.title} 第 ${index + 1} 张`" />
            </button>
          </div>
        </section>

        <section class="info">
          <div class="title-row">
            <h1>{{ item.title }}</h1>
            <el-tag :color="status.color" effect="plain" style="border: none">{{ status.label }}</el-tag>
          </div>
          <p class="meta">
            {{ item.categoryName }} · {{ item.communityName }} · 浏览 {{ item.viewCount }} 次 · 已租
            {{ item.rentCount }} 次
          </p>

          <div class="price-panel">
            <div class="price">
              ¥{{ formatMoney(item.dailyPrice) }}<span class="unit">/天</span>
            </div>
            <div class="price-sides">
              <span>押金 ¥{{ formatMoney(item.deposit) }}</span>
              <span>逾期费率 {{ formatMoney(item.overdueRate) }} 倍/天</span>
            </div>
          </div>

          <el-descriptions :column="1" border class="facts">
            <el-descriptions-item label="物品状态">
              <el-tag :color="maintenance.color" effect="plain" style="border: none">
                {{ maintenance.label }}
              </el-tag>
              <span class="muted hint">损耗值 {{ item.lossValue }}（累计，达阈值会自动提醒保养）</span>
            </el-descriptions-item>
            <el-descriptions-item label="发布者">
              <span>{{ item.owner?.nickname }}</span>
              <span class="muted">（{{ item.owner?.communityName || '未填写小区' }}）</span>
              <el-tag class="credit" :color="'#1d4ed8'" effect="plain" style="border: none">
                信誉 {{ item.owner?.creditScore }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="发布时间">
              {{ formatDateTime(item.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="物品描述">
              <span v-if="item.description">{{ item.description }}</span>
              <span v-else class="muted">发布者没有填写描述</span>
            </el-descriptions-item>
          </el-descriptions>

          <div class="actions">
            <el-button
              v-if="isOwner"
              type="primary"
              plain
              @click="router.push({ name: 'item-publish', query: { edit: item.id } })"
            >
              编辑这件物品
            </el-button>
            <el-tooltip v-else content="下单与费用计算在切片 3 开放（K2 三层防线）" placement="top">
              <span>
                <el-button type="primary" size="large" disabled>立即租用</el-button>
              </span>
            </el-tooltip>
            <span class="muted hint">费用由后端统一计算，前端只展示（规则 F1）</span>
          </div>
        </section>
      </div>

      <section class="calendar-section">
        <h2>可租日期</h2>
        <p class="muted">红色划掉的日子已被别人租走。下单时会再次校验，同一时段只租给一个人。</p>
        <OccupancyCalendar :item-id="item.id" />
      </section>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.back {
  margin-bottom: $space-4;

  .back-title {
    font-size: $font-size-sm;
    color: $color-text-secondary;
  }
}

.detail {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: $space-6;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.main-image {
  aspect-ratio: 4 / 3;
  border-radius: $radius-lg;
  overflow: hidden;
  background: $color-surface-sunken;
  border: 1px solid $color-border;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}

.image-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: $color-text-placeholder;
}

.thumbs {
  display: flex;
  gap: $space-2;
  margin-top: $space-3;
}

.thumb {
  width: 72px;
  height: 72px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: $radius-sm;
  overflow: hidden;
  cursor: pointer;
  background: none;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }

  &.active {
    border-color: $color-primary;
  }
}

.title-row {
  display: flex;
  align-items: center;
  gap: $space-3;

  h1 {
    margin: 0;
    font-size: $font-size-xl;
    color: $color-text;
  }
}

.meta {
  margin: $space-2 0 $space-4;
  font-size: $font-size-sm;
  color: $color-text-secondary;
}

.price-panel {
  padding: $space-4;
  border-radius: $radius-base;
  background: $color-primary-wash;
  border: 1px solid $color-border;
  margin-bottom: $space-4;
}

.price {
  color: $color-primary-deep;
  font-size: 32px;
  font-weight: 700;

  .unit {
    margin-left: 4px;
    font-size: $font-size-sm;
    font-weight: 400;
    color: $color-text-secondary;
  }
}

.price-sides {
  display: flex;
  gap: $space-4;
  margin-top: $space-1;
  font-size: $font-size-xs;
  color: $color-text-secondary;
}

.facts {
  margin-bottom: $space-4;
}

.credit {
  margin-left: $space-2;
}

.hint {
  font-size: $font-size-xs;
  margin-left: $space-2;
}

.actions {
  display: flex;
  align-items: center;
  gap: $space-3;
  flex-wrap: wrap;
}

.calendar-section {
  margin-top: $space-8;

  h2 {
    margin: 0 0 $space-1;
    font-size: $font-size-lg;
    color: $color-text;
  }

  p {
    margin: 0 0 $space-3;
    font-size: $font-size-sm;
  }
}
</style>
