<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ItemCard from '@/components/ItemCard.vue'
import { listItems } from '@/api/item'
import { listCategories } from '@/api/category'
import { getRecommend } from '@/api/recommend'
import { useUserStore } from '@/stores/user'
import { groupCategories } from '@/utils/constants'
import { formatMoney, resolveImageUrl } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const items = ref([])
const total = ref(0)
const categories = ref([])
const recommend = ref({ coldStart: true, list: [], weights: null })

const communityName = computed(() => userStore.profile?.communityName || '')
const recommendTitle = computed(() => (recommend.value.coldStart ? '近期热门' : '为你推荐'))

async function load() {
  loading.value = true
  try {
    const [page, categoryList, recommendResult] = await Promise.all([
      listItems({ pageNum: 1, pageSize: 8 }),
      listCategories().catch(() => []),
      // 推荐失败不能拖垮首页：退化成"没有推荐区"即可（其余内容照常展示）
      getRecommend(6).catch(() => null)
    ])
    items.value = page.list || []
    total.value = page.total || 0
    categories.value = groupCategories(categoryList)
    if (recommendResult) {
      recommend.value = {
        coldStart: recommendResult.coldStart,
        list: recommendResult.list || [],
        weights: recommendResult.weights
      }
    }
  } catch (e) {
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function goCategory(categoryId) {
  // 带上类目跳列表页，列表页会把 query 当初始筛选条件
  router.push({ name: 'items', query: { categoryId } })
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <section class="hero">
      <div class="hero-text">
        <p class="eyebrow">
          <template v-if="userStore.isLogin && communityName">你的小区：{{ communityName }}</template>
          <template v-else>邻里之间，借来借去</template>
        </p>
        <h1>邻居家的电钻<br />不用买，租三天</h1>
        <p class="sub">
          同一时段只租给一个人，费用按天自动结算，用坏了按损耗规则赔。不涉及真实资金，演示环境。
        </p>
        <div class="hero-actions">
          <el-button type="primary" size="large" @click="router.push({ name: 'items' })">
            去看看有什么
          </el-button>
          <el-button v-if="userStore.isLogin" size="large" @click="router.push({ name: 'item-publish' })">
            发布我的闲置
          </el-button>
          <el-button v-else size="large" @click="router.push({ name: 'register' })">注册后发布</el-button>
        </div>
      </div>
      <ul class="hero-points">
        <li>
          <strong>同一时段只有一个人</strong>
          <span>下单时用「预检 + 分布式锁 + 数据库唯一索引」三层防重复占用</span>
        </li>
        <li>
          <strong>结算不用找管理员</strong>
          <span>归还后按日租金、逾期费率、损耗规则自动算出应退押金</span>
        </li>
        <li>
          <strong>每条推荐都有理由</strong>
          <span>推荐结果附「为什么推给你」，不做黑盒排序</span>
        </li>
      </ul>
    </section>

    <section v-if="categories.length" class="categories">
      <button
        v-for="group in categories"
        :key="group.id"
        type="button"
        class="category-chip"
        @click="goCategory(group.id)"
      >
        <span class="chip-name">{{ group.name }}</span>
        <span class="chip-sub">{{ group.children.map((c) => c.name).join(' / ') }}</span>
      </button>
    </section>

    <section v-if="recommend.list.length" class="recommend">
      <header class="latest-head">
        <div>
          <h2>{{ recommendTitle }}</h2>
          <p class="muted">
            <template v-if="recommend.coldStart">
              还没有租赁记录，先看你小区最近被租得最多的物品
            </template>
            <template v-else>
              协同过滤 {{ recommend.weights?.cf }} · 内容相似 {{ recommend.weights?.content }} · 热度
              {{ recommend.weights?.heat }}
            </template>
          </p>
        </div>
        <el-button text @click="router.push({ name: 'items' })">去逛逛 →</el-button>
      </header>

      <div class="recommend-row">
        <router-link
          v-for="item in recommend.list"
          :key="item.id"
          :to="{ name: 'item-detail', params: { id: item.id } }"
          class="rec-card"
        >
          <div class="rec-cover">
            <img :src="resolveImageUrl(item.coverUrl)" :alt="item.title" />
          </div>
          <div class="rec-body">
            <p class="rec-title">{{ item.title }}</p>
            <p class="rec-price">
              ¥{{ formatMoney(item.dailyPrice) }}<span>/天</span>
            </p>
            <p class="rec-reason">· {{ item.reason }}</p>
          </div>
        </router-link>
      </div>
    </section>

    <section class="latest">
      <header class="latest-head">
        <div>
          <h2>最新上架</h2>
          <p class="muted">共 {{ total }} 件物品可租</p>
        </div>
        <el-button text @click="router.push({ name: 'items' })">查看全部 →</el-button>
      </header>

      <div v-loading="loading" class="grid">
        <ItemCard v-for="item in items" :key="item.id" :item="item" />
      </div>
      <el-empty v-if="!loading && items.length === 0" description="还没有上架的物品" />
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.hero {
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  gap: $space-6;
  padding: $space-8;
  border-radius: $radius-lg;
  background: linear-gradient(150deg, #fdf1e6, #fbe0c9 60%, #f8cda8);
  margin-bottom: $space-6;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
    padding: $space-6;
  }
}

.eyebrow {
  margin: 0 0 $space-2;
  font-size: $font-size-sm;
  color: $color-wood;
  letter-spacing: 0.04em;
}

.hero-text h1 {
  margin: 0 0 $space-3;
  font-size: 34px;
  line-height: 1.25;
  color: $color-text;
}

.sub {
  margin: 0 0 $space-4;
  color: $color-text-secondary;
  font-size: $font-size-sm;
  max-width: 30em;
}

.hero-actions {
  display: flex;
  gap: $space-3;
  flex-wrap: wrap;
}

.hero-points {
  margin: 0;
  padding: $space-4 $space-4 $space-4 $space-6;
  list-style: none;
  background: rgba(255, 255, 255, 0.62);
  border-radius: $radius-base;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: $space-3;

  li {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  strong {
    font-size: $font-size-sm;
    color: $color-text;
  }

  span {
    font-size: $font-size-xs;
    color: $color-text-secondary;
    line-height: 1.5;
  }
}

.categories {
  display: flex;
  flex-wrap: wrap;
  gap: $space-2;
  margin-bottom: $space-6;
}

.category-chip {
  flex: 1 1 160px;
  min-width: 150px;
  padding: $space-3 $space-4;
  text-align: left;
  background: $color-surface;
  border: 1px solid $color-border;
  border-radius: $radius-base;
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;

  &:hover {
    border-color: $color-primary;
    box-shadow: $shadow-sm;
  }
}

.chip-name {
  display: block;
  font-size: $font-size-sm;
  font-weight: 600;
  color: $color-text;
}

.chip-sub {
  display: block;
  margin-top: 2px;
  font-size: $font-size-xs;
  color: $color-text-secondary;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommend {
  margin-bottom: $space-6;
}

.recommend-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $space-3;

  @media (max-width: 900px) {
    grid-template-columns: repeat(2, 1fr);
  }

  @media (max-width: 560px) {
    grid-template-columns: 1fr;
  }
}

.rec-card {
  display: flex;
  gap: $space-3;
  padding: $space-3;
  background: $color-surface;
  border: 1px solid $color-border;
  border-radius: $radius-base;
  text-decoration: none;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;

  &:hover {
    border-color: $color-primary;
    box-shadow: $shadow-sm;
  }
}

.rec-cover {
  flex: 0 0 76px;
  height: 76px;
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

.rec-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rec-title {
  margin: 0;
  font-size: $font-size-sm;
  color: $color-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rec-price {
  margin: 0;
  font-size: $font-size-sm;
  font-weight: 700;
  color: $color-primary-deep;

  span {
    font-size: $font-size-xs;
    font-weight: 400;
    color: $color-text-secondary;
  }
}

.rec-reason {
  margin: 0;
  font-size: $font-size-xs;
  color: $color-text-secondary;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.latest-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: $space-4;

  h2 {
    margin: 0;
    font-size: $font-size-lg;
  }

  p {
    margin: 2px 0 0;
    font-size: $font-size-xs;
  }
}

.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $space-4;
  min-height: 200px;

  @media (max-width: 1100px) {
    grid-template-columns: repeat(3, 1fr);
  }

  @media (max-width: 780px) {
    grid-template-columns: repeat(2, 1fr);
  }

  @media (max-width: 520px) {
    grid-template-columns: 1fr;
  }
}
</style>
