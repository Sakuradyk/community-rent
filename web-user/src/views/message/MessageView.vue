<template>
  <div class="page-container message-page">
    <div class="page-head">
      <div>
        <h2>站内消息</h2>
        <p class="muted">
          订单、结算、审核与裁决的变动都会在这里留一条记录。<span v-if="unread > 0">当前有 {{ unread }} 条未读。</span>
        </p>
      </div>
      <div class="head-actions">
        <el-radio-group v-model="unreadOnly" size="small" @change="reload">
          <el-radio-button :value="false">全部</el-radio-button>
          <el-radio-button :value="true">只看未读</el-radio-button>
        </el-radio-group>
        <el-button :disabled="unread === 0" @click="handleReadAll">全部已读</el-button>
      </div>
    </div>

    <el-empty v-if="!loading && list.length === 0" description="暂时没有消息" />

    <div v-else v-loading="loading" class="msg-list">
      <div
        v-for="msg in list"
        :key="msg.id"
        class="msg-item"
        :class="{ unread: !msg.read }"
        @click="openMessage(msg)"
      >
        <span class="dot" aria-hidden="true" />
        <div class="msg-main">
          <div class="msg-title-row">
            <el-tag :type="typeOf(msg.type).tag" size="small" effect="plain">
              {{ msg.typeLabel || typeOf(msg.type).label }}
            </el-tag>
            <span class="msg-title">{{ msg.title }}</span>
            <span class="msg-time muted">{{ formatDateTime(msg.createdAt) }}</span>
          </div>
          <div class="msg-content">{{ msg.content }}</div>
        </div>
        <el-button
          v-if="msg.bizType"
          link
          type="primary"
          class="msg-link"
          @click.stop="openMessage(msg)"
        >
          查看详情
        </el-button>
      </div>
    </div>

    <div v-if="total > pageSize" class="pager">
      <el-pagination
        v-model:current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        background
        @current-change="reload"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listMessages, markAllMessagesRead, markMessageRead } from '@/api/message'
import { BIZ_ROUTE, MESSAGE_TYPE, statusOf } from '@/utils/constants'
import { formatDateTime } from '@/utils/format'

const router = useRouter()

const list = ref([])
const loading = ref(false)
const unreadOnly = ref(false)
const unread = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const typeOf = (type) => statusOf(MESSAGE_TYPE, type)

async function reload() {
  loading.value = true
  try {
    pageNum.value = Math.max(1, pageNum.value)
    const data = await listMessages({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      unreadOnly: unreadOnly.value
    })
    list.value = data.list
    total.value = data.total
    unread.value = list.value.filter((m) => !m.read).length
  } catch (e) {
    // 提示由 request.js 统一弹出
  } finally {
    loading.value = false
  }
}

/**
 * 点击消息：先标记已读（幂等），再按 bizType 跳到对应详情页。
 * bizType 为空的消息（系统公告）只标记已读、不跳转。
 */
async function openMessage(msg) {
  if (!msg.read) {
    try {
      await markMessageRead(msg.id)
      msg.read = true
      unread.value = Math.max(0, unread.value - 1)
    } catch (e) {
      // 失败不阻塞跳转
    }
  }
  const routeName = BIZ_ROUTE[msg.bizType]
  if (!routeName || !msg.bizId) return
  const params = routeName === 'settlement' ? { orderId: msg.bizId } : { id: msg.bizId }
  router.push({ name: routeName, params })
}

async function handleReadAll() {
  try {
    const count = await markAllMessagesRead()
    ElMessage.success(count > 0 ? `已标记 ${count} 条为已读` : '没有未读消息')
    await reload()
  } catch (e) {
    // 提示由 request.js 统一弹出
  }
}

onMounted(reload)
</script>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.message-page {
  padding: $space-6 0 $space-12;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $space-4;
  margin-bottom: $space-4;

  h2 {
    margin: 0 0 $space-1;
    font-size: $font-size-xl;
    color: $color-text;
  }

  p {
    margin: 0;
    font-size: $font-size-sm;
  }
}

.head-actions {
  display: flex;
  align-items: center;
  gap: $space-3;
  flex-shrink: 0;
}

.msg-list {
  display: flex;
  flex-direction: column;
  gap: $space-2;
}

.msg-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: $space-3;
  padding: $space-4;
  border: 1px solid $color-border;
  border-radius: $radius-base;
  background: $color-surface;
  transition: box-shadow 0.2s, border-color 0.2s;

  &:hover {
    border-color: $color-border-strong;
    box-shadow: $shadow-sm;
  }

  .dot {
    width: 8px;
    height: 8px;
    margin-top: 8px;
    border-radius: 50%;
    background: transparent;
    flex-shrink: 0;
  }

  &.unread {
    background: $color-primary-wash;

    .dot {
      background: $color-primary;
    }

    .msg-title {
      font-weight: 600;
    }
  }
}

.msg-main {
  flex: 1;
  min-width: 0;
}

.msg-title-row {
  display: flex;
  align-items: center;
  gap: $space-2;
  flex-wrap: wrap;
}

.msg-title {
  font-size: $font-size-md;
  color: $color-text;
}

.msg-time {
  margin-left: auto;
  font-size: $font-size-xs;
}

.msg-content {
  margin-top: $space-1;
  font-size: $font-size-sm;
  color: $color-text-secondary;
  line-height: 1.6;
  word-break: break-word;
}

.msg-link {
  flex-shrink: 0;
  align-self: center;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: $space-6;
}
</style>
