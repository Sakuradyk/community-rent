<template>
  <div class="page-container">
    <div class="card wallet-card">
      <div class="wallet-head">
        <div>
          <div class="wallet-title">我的钱包</div>
          <div class="muted small">下单冻结「租金 + 押金」，结算后多退少补</div>
        </div>
        <div class="wallet-actions">
          <el-button size="small" @click="router.push({ name: 'wallet' })">账单明细</el-button>
          <el-button type="primary" size="small" @click="openRecharge">充值</el-button>
        </div>
      </div>
      <div class="wallet-grid">
        <div class="wallet-item">
          <div class="wallet-value">¥{{ money(wallet.cashBalance) }}</div>
          <div class="muted small">可用余额</div>
        </div>
        <div class="wallet-item">
          <div class="wallet-value wallet-frozen">¥{{ money(wallet.frozenBalance) }}</div>
          <div class="muted small">冻结中</div>
        </div>
        <div class="wallet-item">
          <div class="wallet-value">¥{{ money(wallet.totalBalance) }}</div>
          <div class="muted small">账户总额</div>
        </div>
      </div>
    </div>

    <el-dialog v-model="rechargeVisible" title="充值" width="380px">
      <div class="preset-row">
        <el-button
          v-for="p in RECHARGE_PRESETS"
          :key="p"
          size="small"
          :type="rechargeForm.amount === p ? 'primary' : ''"
          @click="rechargeForm.amount = p"
        >{{ p }} 元</el-button>
      </div>
      <el-input-number
        v-model="rechargeForm.amount"
        :min="1"
        :max="LIMITS.RECHARGE_MAX"
        :precision="2"
        style="width: 100%; margin-top: 12px"
      />
      <div class="muted small" style="margin-top: 8px">
        单次最多 {{ LIMITS.RECHARGE_MAX }} 元。演示用途：点确认即到账，没有真实支付渠道。
      </div>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" :loading="rechargeLoading" @click="submitRecharge">确认充值</el-button>
      </template>
    </el-dialog>
    <div class="card" v-if="userStore.profile">
      <div class="head">
        <el-upload
          class="avatar-upload"
          :show-file-list="false"
          :http-request="handleAvatarUpload"
          :before-upload="beforeAvatarUpload"
          accept="image/png,image/jpeg,image/webp"
        >
          <el-avatar :size="72" :src="avatarUrl">
            {{ (userStore.profile.nickname || '?').slice(0, 1) }}
          </el-avatar>
          <div class="avatar-tip muted">点击换头像</div>
        </el-upload>

        <div class="meta">
          <div class="nickname">{{ userStore.profile.nickname }}</div>
          <div class="muted">{{ maskPhone(userStore.profile.phone) }}</div>
        </div>

        <div class="credit">
          <div class="credit-value">{{ userStore.profile.creditScore }}</div>
          <div class="muted">信誉分</div>
        </div>
      </div>

      <el-divider />

      <el-form label-width="96px" style="max-width: 520px">
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" :maxlength="LIMITS.NICKNAME_MAX" show-word-limit />
        </el-form-item>
        <el-form-item label="所在小区">
          <el-select v-model="form.communityId" placeholder="未选择" clearable style="width: 100%">
            <el-option
              v-for="c in communities"
              :key="c.id"
              :label="`${c.name}（${c.district}）`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="注册时间">
          <span class="muted">{{ formatDateTime(userStore.profile.createdAt) }}</span>
        </el-form-item>
        <el-form-item label="账号状态">
          <el-tag :type="statusOf(USER_STATUS, userStore.profile.status).tag" size="small">
            {{ statusOf(USER_STATUS, userStore.profile.status).label }}
          </el-tag>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存资料</el-button>
          <el-button @click="handleLogout">退出登录</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-empty v-else description="正在加载资料…" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { listCommunities } from '@/api/community'
import { uploadImage } from '@/api/file'
import { LIMITS, RECHARGE_PRESETS, USER_STATUS, statusOf } from '@/utils/constants'
import { getWallet, recharge } from '@/api/wallet'
import { maskPhone, formatDateTime } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

const communities = ref([])
const saving = ref(false)
const uploading = ref(false)
const form = reactive({ nickname: '', communityId: null })

const avatarUrl = computed(() => userStore.profile?.avatarUrl || '')

// ---------------- 钱包（可用 / 冻结 / 总额 + 模拟充值）
const wallet = reactive({ accountId: null, cashBalance: 0, frozenBalance: 0, totalBalance: 0 })
const rechargeVisible = ref(false)
const rechargeLoading = ref(false)
const rechargeForm = reactive({ amount: 50 })

function money(v) {
  return Number(v || 0).toFixed(2)
}

async function loadWallet() {
  try {
    const data = await getWallet()
    if (data) {
      wallet.accountId = data.accountId
      wallet.cashBalance = data.cashBalance
      wallet.frozenBalance = data.frozenBalance
      wallet.totalBalance = data.totalBalance
    }
  } catch (e) {
    // 钱包加载失败不阻塞资料页：顶栏与下单页仍以后端为准
  }
}

function openRecharge() {
  rechargeForm.amount = RECHARGE_PRESETS[0]
  rechargeVisible.value = true
}

async function submitRecharge() {
  if (!rechargeForm.amount || rechargeForm.amount <= 0) {
    ElMessage.warning('请输入大于 0 的金额')
    return
  }
  rechargeLoading.value = true
  try {
    const data = await recharge(rechargeForm.amount)
    if (data) {
      wallet.cashBalance = data.cashBalance
      wallet.frozenBalance = data.frozenBalance
      wallet.totalBalance = data.totalBalance
    }
    rechargeVisible.value = false
    ElMessage.success(`充值 ${money(rechargeForm.amount)} 元成功`)
  } finally {
    rechargeLoading.value = false
  }
}

function syncForm(p) {
  form.nickname = p?.nickname || ''
  form.communityId = p?.communityId || null
}

watch(() => userStore.profile, syncForm, { immediate: true })

onMounted(async () => {
  loadWallet()
  try {
    communities.value = await listCommunities()
  } catch (e) {
    // 字典失败不阻塞页面
  }
})

function beforeAvatarUpload(file) {
  const okType = ['image/png', 'image/jpeg', 'image/webp'].includes(file.type)
  if (!okType) {
    ElMessage.error('只支持 jpg / png / webp 图片')
    return false
  }
  if (file.size > LIMITS.IMAGE_MAX_MB * 1024 * 1024) {
    ElMessage.error(`图片不能超过 ${LIMITS.IMAGE_MAX_MB}MB`)
    return false
  }
  return true
}

async function handleAvatarUpload(options) {
  uploading.value = true
  try {
    const { url } = await uploadImage(options.file)
    await userStore.updateProfile({ avatarUrl: url })
    ElMessage.success('头像已更新')
  } catch (e) {
    // 2004 / 2005 的提示由 request.js 弹出
  } finally {
    uploading.value = false
  }
}

async function handleSave() {
  if (!form.nickname || form.nickname.length < LIMITS.NICKNAME_MIN) {
    ElMessage.warning(`昵称至少 ${LIMITS.NICKNAME_MIN} 个字`)
    return
  }
  saving.value = true
  try {
    await userStore.updateProfile({
      nickname: form.nickname,
      communityId: form.communityId || null
    })
    ElMessage.success('资料已保存')
  } catch (e) {
    // 统一提示
  } finally {
    saving.value = false
  }
}

async function handleLogout() {
  await userStore.logout()
  ElMessage.success('已退出登录')
  router.push({ name: 'home' })
}
</script>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.head {
  display: flex;
  align-items: center;
  gap: 20px;
}

.avatar-upload { text-align: center; cursor: pointer; }
.avatar-tip { margin-top: 6px; }

.meta { flex: 1; }
.nickname { font-size: 18px; font-weight: 600; margin-bottom: 4px; }

.credit { text-align: right; }
.credit-value { font-size: 28px; font-weight: 700; color: $color-primary; }
</style>

<style scoped lang="scss">

@use '@/assets/styles/variables.scss' as *;.wallet-card {
  .wallet-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $space-3;
  }

  .wallet-title {
    font-size: 16px;
    font-weight: 600;
    color: $color-text;
  }

  .wallet-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: $space-3;
    margin-top: $space-3;
    padding-top: $space-3;
    border-top: 1px dashed $color-border;
  }

  .wallet-item {
    text-align: center;
  }

  .wallet-value {
    font-size: 22px;
    font-weight: 700;
    color: $color-primary-deep;
    line-height: 1.4;
  }

  .wallet-frozen {
    color: $color-text-secondary;
  }
}

.preset-row {
  display: flex;
  gap: $space-2;
  flex-wrap: wrap;
}
</style>