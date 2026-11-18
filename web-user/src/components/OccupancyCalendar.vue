<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { getItemCalendar } from '@/api/item'

/**
 * 占用日历（只读展示）。
 *
 * 规则 F1：前端不算冲突、不算日期可用性——哪些天被占一律来自后端
 * GET /api/items/{id}/calendar，这里只负责画格子。
 * 切片 3 做下单时会在此基础上加"选择区间"，选择结果仍需交后端校验（4090 兜底）。
 */
const props = defineProps({
  itemId: { type: [String, Number], required: true }
})

const emit = defineEmits(['loaded'])

const month = ref(dayjs().format('YYYY-MM'))
const occupied = ref(new Set())
const loading = ref(false)
const failed = ref(false)

const monthTitle = computed(() => month.value.replace('-', ' 年 ') + ' 月')

const cells = computed(() => {
  const first = dayjs(`${month.value}-01`)
  // 周一为第一天：dayjs 的 day() 里周日是 0，这里换算成 0=周一 … 6=周日
  const leading = (first.day() + 6) % 7
  const total = first.daysInMonth()
  const today = dayjs().format('YYYY-MM-DD')
  const list = []
  for (let i = 0; i < leading; i += 1) {
    list.push({ key: `blank-${i}`, blank: true })
  }
  for (let d = 1; d <= total; d += 1) {
    const date = first.date(d).format('YYYY-MM-DD')
    list.push({
      key: date,
      blank: false,
      day: d,
      date,
      occupied: occupied.value.has(date),
      today: date === today,
      past: dayjs(date).isBefore(dayjs(), 'day')
    })
  }
  return list
})

async function load() {
  loading.value = true
  failed.value = false
  try {
    const data = await getItemCalendar(props.itemId, month.value)
    occupied.value = new Set(data.occupiedDates || [])
    emit('loaded', data)
  } catch (e) {
    // 提示已由 request.js 统一弹出，这里只把日历置为不可用状态
    failed.value = true
    occupied.value = new Set()
  } finally {
    loading.value = false
  }
}

function shift(delta) {
  month.value = dayjs(`${month.value}-01`).add(delta, 'month').format('YYYY-MM')
}

watch(month, load)
onMounted(load)
</script>

<template>
  <div class="calendar" v-loading="loading">
    <div class="head">
      <el-button text :disabled="month === dayjs().format('YYYY-MM')" @click="shift(-1)">上一月</el-button>
      <span class="title">{{ monthTitle }}</span>
      <el-button text @click="shift(1)">下一月</el-button>
    </div>

    <div class="weekdays">
      <span v-for="w in ['一', '二', '三', '四', '五', '六', '日']" :key="w">{{ w }}</span>
    </div>

    <div class="grid">
      <span
        v-for="cell in cells"
        :key="cell.key"
        class="cell"
        :class="{
          blank: cell.blank,
          occupied: cell.occupied,
          today: cell.today,
          past: cell.past
        }"
      >
        <template v-if="!cell.blank">{{ cell.day }}</template>
      </span>
    </div>

    <div class="legend">
      <span><i class="dot dot-free" />可租</span>
      <span><i class="dot dot-occupied" />已被租出</span>
      <span><i class="dot dot-today" />今天</span>
    </div>

    <el-alert
      v-if="failed"
      type="warning"
      :closable="false"
      show-icon
      title="日历加载失败，请稍后重试（不影响浏览其他信息）"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.calendar {
  background: $color-surface;
  border: 1px solid $color-border;
  border-radius: $radius-lg;
  padding: $space-4;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $space-3;

  .title {
    font-size: $font-size-md;
    font-weight: 600;
    color: $color-text;
  }
}

.weekdays,
.grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: $space-1;
}

.weekdays {
  margin-bottom: $space-1;
  font-size: $font-size-xs;
  color: $color-text-secondary;
  text-align: center;
}

.cell {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  border-radius: $radius-sm;
  font-size: $font-size-sm;
  color: $color-text;

  &.blank {
    background: transparent;
  }

  &.occupied {
    background: rgba(185, 28, 28, 0.1);
    color: $color-overdue;
    text-decoration: line-through;
  }

  &.past {
    color: $color-text-placeholder;
  }

  &.today {
    outline: 2px solid $color-accent;
    outline-offset: -2px;
    font-weight: 600;
  }
}

.legend {
  display: flex;
  gap: $space-4;
  margin-top: $space-3;
  font-size: $font-size-xs;
  color: $color-text-secondary;

  span {
    display: inline-flex;
    align-items: center;
    gap: $space-1;
  }
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.dot-free {
  background: $color-surface-sunken;
  border: 1px solid $color-border-strong;
}

.dot-occupied {
  background: $color-overdue;
}

.dot-today {
  background: $color-accent;
}
</style>
