<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ItemCard from '@/components/ItemCard.vue'
import { listItems } from '@/api/item'
import { listCategories } from '@/api/category'
import { listCommunities } from '@/api/community'
import { useUserStore } from '@/stores/user'
import { LIMITS, groupCategories } from '@/utils/constants'
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const items = ref([])
const total = ref(0)
const categories = ref([])
const communities = ref([])

const query = reactive({
  keyword: '',
  categoryId: null,
  communityId: null,
  minPrice: null,
  maxPrice: null,
  dateRange: [],
  pageNum: 1,
  pageSize: LIMITS.PAGE_SIZE
})

const dateRangeProps = {
  // 只能选今天及以后：租用区间落在过去没有意义
  disabledDate: (date) => date.getTime() < Date.now() - 24 * 60 * 60 * 1000
}

async function load() {
  loading.value = true
  try {
    const params = {
      pageNum: query.pageNum,
      pageSize: query.pageSize
    }
    if (query.keyword) params.keyword = query.keyword
    if (query.categoryId) params.categoryId = query.categoryId
    if (query.communityId) params.communityId = query.communityId
    if (query.minPrice !== null && query.minPrice !== '') params.minPrice = query.minPrice
    if (query.maxPrice !== null && query.maxPrice !== '') params.maxPrice = query.maxPrice
    // 契约 §2.4：startDate/endDate 必须成对出现，否则后端报 2001
    if (query.dateRange && query.dateRange.length === 2) {
      params.startDate = query.dateRange[0]
      params.endDate = query.dateRange[1]
    }
    const data = await listItems(params)
    items.value = data.list || []
    total.value = data.total || 0
  } catch (e) {
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function reset() {
  query.keyword = ''
  query.categoryId = null
  query.communityId = null
  query.minPrice = null
  query.maxPrice = null
  query.dateRange = []
  search()
}

function changePage(page) {
  query.pageNum = page
  load()
}

onMounted(async () => {
  // 首页/类目入口会带 ?categoryId= 过来，作为初始筛选条件
  if (route.query.categoryId) query.categoryId = route.query.categoryId
  const [categoryList, communityList] = await Promise.all([
    listCategories().catch(() => []),
    listCommunities().catch(() => [])
  ])
  categories.value = groupCategories(categoryList)
  communities.value = communityList
  load()
})
</script>

<template>
  <div class="page-container">
    <header class="page-head">
      <div>
        <h1>逛物品</h1>
        <p class="muted">
          邻居家闲置的东西，按天租用。
          <template v-if="userStore.isLogin && userStore.profile?.communityId">
            登录状态下会优先展示你所在小区的物品。
          </template>
          <template v-else>登录后优先展示你所在小区的物品。</template>
        </p>
      </div>
      <el-button type="primary" @click="router.push({ name: 'item-publish' })">发布我的物品</el-button>
    </header>

    <section class="filters card">
      <div class="filter-row">
        <el-input
          v-model="query.keyword"
          placeholder="搜物品名称或描述"
          clearable
          class="w-220"
          @keyup.enter="search"
        />
        <el-select v-model="query.categoryId" placeholder="全部类目" clearable class="w-180">
          <el-option-group v-for="group in categories" :key="group.id" :label="group.name">
            <el-option :label="group.name" :value="group.id" />
            <el-option
              v-for="child in group.children"
              :key="child.id"
              :label="`${group.name} / ${child.name}`"
              :value="child.id"
            />
          </el-option-group>
        </el-select>
        <el-select v-model="query.communityId" placeholder="全部小区" clearable class="w-180">
          <el-option v-for="c in communities" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-input-number v-model="query.minPrice" :min="0" :controls="false" placeholder="最低日租" class="w-110" />
        <span class="dash">—</span>
        <el-input-number v-model="query.maxPrice" :min="0" :controls="false" placeholder="最高日租" class="w-110" />
      </div>
      <div class="filter-row">
        <el-date-picker
          v-model="query.dateRange"
          type="daterange"
          unlink-panels
          range-separator="至"
          start-placeholder="取用日期"
          end-placeholder="归还日期"
          value-format="YYYY-MM-DD"
          :disabled-date="dateRangeProps.disabledDate"
          class="w-320"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <span class="muted result-count">共 {{ total }} 件</span>
      </div>
    </section>

    <section v-loading="loading" class="grid-wrap">
      <div v-if="items.length" class="grid">
        <ItemCard v-for="item in items" :key="item.id" :item="item" />
      </div>
      <el-empty v-else-if="!loading" description="没有符合条件的物品，换个条件试试" />
    </section>

    <div v-if="total > query.pageSize" class="pager">
      <el-pagination
        background
        layout="prev, pager, next"
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        :total="total"
        @current-change="changePage"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $space-4;
  margin-bottom: $space-4;

  h1 {
    margin: 0 0 $space-1;
    font-size: $font-size-xl;
    color: $color-text;
  }

  p {
    margin: 0;
  }
}

.filters {
  margin-bottom: $space-6;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: $space-2;

  & + .filter-row {
    margin-top: $space-3;
  }
}

.w-110 {
  width: 110px;
}

.w-180 {
  width: 180px;
}

.w-220 {
  width: 220px;
}

.w-320 {
  width: 320px;
}

.dash {
  color: $color-text-placeholder;
}

.result-count {
  margin-left: auto;
  font-size: $font-size-sm;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $space-4;

  @media (max-width: 1024px) {
    grid-template-columns: repeat(2, 1fr);
  }

  @media (max-width: 640px) {
    grid-template-columns: 1fr;
  }
}

.grid-wrap {
  min-height: 240px;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: $space-6;
}
</style>
