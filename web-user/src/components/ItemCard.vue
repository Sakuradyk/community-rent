<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { formatMoney, resolveImageUrl } from '@/utils/format'

const props = defineProps({
  item: { type: Object, required: true }
})

const userStore = useUserStore()

const cover = computed(() => resolveImageUrl(props.item.coverUrl))
// available 只在前端传了日期区间时才有值（后端按区间与占用表比对），null 表示"未查询"
const availability = computed(() => props.item.available)
// 同小区徽标只在"我填了社区且与物品同社区"时亮；纯展示标记，排序与筛选仍由后端 SQL 决定（F1）
const sameCommunity = computed(() => {
  const mine = userStore.profile?.communityId
  return Boolean(mine && props.item.communityId && String(mine) === String(props.item.communityId))
})
</script>

<template>
  <router-link class="item-card" :to="{ name: 'item-detail', params: { id: item.id } }">
    <div class="cover">
      <img v-if="cover" :src="cover" :alt="item.title" loading="lazy" />
      <div v-else class="cover-fallback">暂无图片</div>
      <span v-if="sameCommunity" class="badge badge-same">同小区</span>
      <span v-if="availability === true" class="badge badge-ok">所选日期可租</span>
      <span v-else-if="availability === false" class="badge badge-bad">所选日期已租出</span>
    </div>
    <div class="body">
      <h3 class="title">{{ item.title }}</h3>
      <p class="meta">{{ item.categoryName }} · {{ item.communityName }}</p>
      <div class="price-row">
        <span class="price">¥{{ formatMoney(item.dailyPrice) }}<span class="unit">/天</span></span>
        <span class="deposit">押金 ¥{{ formatMoney(item.deposit) }}</span>
      </div>
      <p class="rent">已被租用 {{ item.rentCount }} 次</p>
    </div>
  </router-link>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.item-card {
  display: block;
  background: $color-surface;
  border: 1px solid $color-border;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  overflow: hidden;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: $shadow-base;
    border-color: $color-border-strong;
  }
}

.cover {
  position: relative;
  aspect-ratio: 4 / 3;
  background: $color-surface-sunken;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}

.cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: $color-text-placeholder;
  font-size: $font-size-sm;
}

// 徽标基类必须先写：变体只覆盖自己需要的属性，否则后写的 top/left 会把变体布局改回去
// （曾经把 .badge-same 写在 .badge 前面，结果 top 与 bottom 同时生效 → 徽标被拉成竖长条）
.badge {
  position: absolute;
  top: $space-2;
  left: $space-2;
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  border-radius: 4px; // 小长方形，不抢图片的视觉
  font-size: 11px;
  line-height: 1;
  white-space: nowrap;
  color: #fff;
}

// 同小区：左上角，白底暖字，体积最小
.badge-same {
  background: rgba(255, 255, 255, 0.94);
  color: $color-primary-deep;
  border: 1px solid $color-primary-tint;
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(120, 82, 45, 0.12);
}

// 「所选日期可租/已租出」靠右上角，避免和同小区徽标叠在一起
.badge-ok,
.badge-bad {
  left: auto;
  right: $space-2;
}

.badge-ok {
  background: rgba(21, 128, 61, 0.92);
}

.badge-bad {
  background: rgba(185, 28, 28, 0.92);
}

.body {
  padding: $space-3 $space-4 $space-4;
}

.title {
  margin: 0 0 $space-1;
  font-size: $font-size-md;
  font-weight: 600;
  color: $color-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  margin: 0 0 $space-2;
  font-size: $font-size-xs;
  color: $color-text-secondary;
}

.price-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: $space-2;
}

.price {
  color: $color-primary-deep;
  font-size: $font-size-lg;
  font-weight: 700;

  .unit {
    margin-left: 2px;
    font-size: $font-size-xs;
    font-weight: 400;
    color: $color-text-secondary;
  }
}

.deposit {
  font-size: $font-size-xs;
  color: $color-text-secondary;
}

.rent {
  margin: $space-2 0 0;
  font-size: $font-size-xs;
  color: $color-text-placeholder;
}
</style>
