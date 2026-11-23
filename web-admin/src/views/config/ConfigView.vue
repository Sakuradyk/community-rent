<template>
  <div v-loading="loading">
    <h2 class="title">参数配置</h2>
    <p class="muted">
      所有业务阈值都在这里，代码里没有第二份。保存后立即生效（后端每次读库、不缓存），
      扫描类参数的 cron 由定时任务每分钟比对一次。
    </p>

    <el-tabs v-model="tab" class="tabs">
      <!-- 三张策略表：只提交改动的字段 -->
      <el-tab-pane
        v-for="group in policyGroups"
        :key="group.type"
        :label="group.label"
        :name="group.type"
      >
        <el-alert type="info" :closable="false" class="hint" :title="group.hint" />
        <el-form label-width="190px" label-position="right" class="policy-form">
          <el-form-item v-for="field in group.fields" :key="field.key" :label="field.label">
            <el-input-number
              v-model="policyForm[group.type][field.key]"
              :step="field.step"
              :precision="field.precision"
              :min="field.min"
              controls-position="right"
              style="width: 200px"
            />
            <span class="field-hint">{{ field.hint }}</span>
          </el-form-item>
        </el-form>
        <div class="actions">
          <el-button type="primary" :loading="saving === group.type" @click="savePolicy(group.type)">
            保存{{ group.label }}
          </el-button>
          <el-button @click="resetPolicy(group.type)">撤销改动</el-button>
          <span v-if="changedCount(group.type) > 0" class="muted">
            已改动 {{ changedCount(group.type) }} 项
          </span>
        </div>
      </el-tab-pane>

      <!-- sys_config 键值表：逐行保存 -->
      <el-tab-pane label="系统参数" name="sys">
        <p class="muted">
          只能修改已存在的键（后端白名单把关，不会凭空造出没有消费方的参数）。
          INT 型带区间校验，BOOLEAN 只能 0/1，CRON 必须是六段表达式。
        </p>
        <el-table :data="sysRows" size="small">
          <el-table-column prop="group" label="分组" width="110" />
          <el-table-column prop="key" label="键" width="250" />
          <el-table-column prop="description" label="说明" />
          <el-table-column prop="valueType" label="类型" width="90" />
          <el-table-column label="当前值" width="240">
            <template #default="{ row }">
              <el-input v-model="row.editValue" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button
                size="small"
                type="primary"
                :disabled="String(row.editValue) === String(row.value)"
                :loading="saving === row.key"
                @click="saveSys(row)"
              >
                保存
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getConfig, updatePolicy, updateSysConfig } from '@/api/config'

const loading = ref(false)
const saving = ref('')
const tab = ref('credit')
const sysRows = ref([])

const policyForm = reactive({ credit: {}, fee: {}, loss: {} })
const original = reactive({ credit: {}, fee: {}, loss: {} })

/**
 * 字段元数据：label 是给人看的，key 必须与实体字段（也是列名驼峰）完全一致 ——
 * 后端只认这个白名单，写错就是 2001 或者被忽略。
 */
const policyGroups = [
  {
    type: 'credit',
    label: '信誉策略',
    hint: '初始 80 分、区间 [0,100]；免押门槛 95、正常档 60–94；扣分项存负数（如 -5）。',
    fields: [
      { key: 'initScore', label: '初始信誉分', step: 1, precision: 0, hint: '新用户注册时的分数' },
      { key: 'minScore', label: '最低分', step: 1, precision: 0, hint: 'clamp 下限' },
      { key: 'maxScore', label: '最高分', step: 1, precision: 0, hint: 'clamp 上限' },
      { key: 'rewardOnTime', label: '按时归还奖励', step: 1, precision: 0, hint: '每单 +2' },
      { key: 'rewardDailyCap', label: '每日奖励上限', step: 1, precision: 0, hint: '当日累计最多加分' },
      { key: 'penaltyOverdueLight', label: '轻逾期扣分', step: 1, precision: 0, hint: '≤3 天，每天扣' },
      { key: 'penaltyOverdueHeavy', label: '重逾期扣分', step: 1, precision: 0, hint: '>3 天，每天扣' },
      { key: 'penaltyDamage', label: '损坏扣分', step: 1, precision: 0, hint: '一次性' },
      { key: 'penaltyCancelAfterConfirm', label: '确认后取消扣分', step: 1, precision: 0, hint: '一次性' },
      { key: 'tierFreeDepositMin', label: '免押门槛', step: 1, precision: 0, hint: '≥ 此分免押金' },
      { key: 'tierNormalMin', label: '正常档下限', step: 1, precision: 0, hint: '正常押金起点' },
      { key: 'tierNormalMax', label: '正常档上限', step: 1, precision: 0, hint: '必须小于免押门槛' },
      { key: 'tierDepositUpMin', label: '加成档下限', step: 1, precision: 0, hint: '押金上浮区间起点' },
      { key: 'tierDepositUpMax', label: '加成档上限', step: 1, precision: 0, hint: '押金上浮区间终点' },
      { key: 'tierDepositUpRatio', label: '押金加成倍数', step: 0.1, precision: 2, hint: '如 1.5' },
      { key: 'tierSuspendBelow', label: '暂停租用线', step: 1, precision: 0, hint: '低于此分不能下单' }
    ]
  },
  {
    type: 'fee',
    label: '费用策略',
    hint: '日租金上下限决定发布页可填范围；押金倍数决定押金范围；逾期费按两档费率计算。',
    fields: [
      { key: 'overdueTier1Days', label: '逾期第一档天数', step: 1, precision: 0, hint: '前 N 天按第一档' },
      { key: 'overdueTier1Rate', label: '逾期第一档费率', step: 0.1, precision: 4, hint: '× 日租金 / 天' },
      { key: 'overdueTier2Rate', label: '逾期第二档费率', step: 0.1, precision: 4, hint: '超出部分按此费率' },
      { key: 'overdueRateMin', label: '逾期费率下限', step: 0.1, precision: 4, hint: '发布页可填最小值' },
      { key: 'overdueRateMax', label: '逾期费率上限', step: 0.1, precision: 4, hint: '发布页可填最大值' },
      { key: 'dailyPriceMin', label: '日租金下限', step: 1, precision: 2, hint: '元' },
      { key: 'dailyPriceMax', label: '日租金上限', step: 10, precision: 2, hint: '元' },
      { key: 'depositRatioMin', label: '押金倍数下限', step: 1, precision: 2, hint: '押金 ≥ 日租金 × 此值' },
      { key: 'depositRatioMax', label: '押金倍数上限', step: 1, precision: 2, hint: '押金 ≤ 日租金 × 此值' },
      { key: 'cancelFreeHours', label: '免费取消小时数', step: 1, precision: 0, hint: '确认后多少小时内取消免扣分' }
    ]
  },
  {
    type: 'loss',
    label: '损耗策略',
    hint: '损耗权重 w = 租期系数 × 类目系数 × 归还状态系数；累计 L ≥ 保养阈值进保养，≥ 检修阈值强制检修。',
    fields: [
      { key: 'thresholdMaintain', label: '保养阈值', step: 0.05, precision: 4, hint: '如 0.6' },
      { key: 'thresholdRepair', label: '检修阈值', step: 0.05, precision: 4, hint: '必须大于保养阈值' },
      { key: 'termTier1MaxDays', label: '租期第一档天数', step: 1, precision: 0, hint: '≤ N 天用第一档系数' },
      { key: 'termTier1Coef', label: '租期第一档系数', step: 0.01, precision: 4, hint: '如 0.01' },
      { key: 'termTier2MaxDays', label: '租期第二档天数', step: 1, precision: 0, hint: '≤ N 天用第二档' },
      { key: 'termTier2Coef', label: '租期第二档系数', step: 0.01, precision: 4, hint: '如 0.02' },
      { key: 'termTier3Coef', label: '租期第三档系数', step: 0.01, precision: 4, hint: '超出第二档' },
      { key: 'returnCoefIntact', label: '完好系数', step: 0.1, precision: 4, hint: '通常 1.0' },
      { key: 'returnCoefWorn', label: '轻微磨损系数', step: 0.1, precision: 4, hint: '通常 1.5' },
      { key: 'returnCoefDamaged', label: '损坏系数', step: 0.1, precision: 4, hint: '通常 3.0' }
    ]
  }
]

const changedCount = computed(() => (type) => {
  const changed = diffOf(type)
  return Object.keys(changed).length
})

function diffOf(type) {
  const changed = {}
  for (const field of policyGroups.find((g) => g.type === type).fields) {
    const now = policyForm[type][field.key]
    const before = original[type][field.key]
    if (now === null || now === undefined) continue
    if (before === null || before === undefined || String(now) !== String(before)) {
      changed[field.key] = now
    }
  }
  return changed
}

function fillPolicy(type, policy) {
  policyForm[type] = {}
  original[type] = {}
  for (const field of policyGroups.find((g) => g.type === type).fields) {
    const value = policy ? Number(policy[field.key]) : null
    policyForm[type][field.key] = value
    original[type][field.key] = value
  }
}

async function load() {
  loading.value = true
  try {
    const data = await getConfig()
    fillPolicy('credit', data.creditPolicy)
    fillPolicy('fee', data.feePolicy)
    fillPolicy('loss', data.lossPolicy)
    sysRows.value = (data.sysConfig || []).map((item) => ({ ...item, editValue: item.value }))
  } catch {
    // 提示已由 request.js 统一弹出
  } finally {
    loading.value = false
  }
}

/** 只提交改动的字段：避免把整表回填，冲掉别人刚改的值 */
async function savePolicy(type) {
  const changed = diffOf(type)
  if (Object.keys(changed).length === 0) {
    ElMessage.info('没有改动')
    return
  }
  saving.value = type
  try {
    await updatePolicy(type, changed)
    ElMessage.success('已保存，立即生效')
    await load()
  } catch {
    // 提示已由 request.js 统一弹出（越界校验的文案来自后端）
  } finally {
    saving.value = ''
  }
}

function resetPolicy(type) {
  fillPolicy(type, original[type])
  ElMessage.info('已撤销未保存的改动')
}

async function saveSys(row) {
  saving.value = row.key
  try {
    const item = await updateSysConfig(row.key, String(row.editValue))
    row.value = item.value
    row.editValue = item.value
    ElMessage.success(`${row.key} 已保存（立即生效）`)
  } catch {
    // 越界/类型不合法由后端返回 2001，提示已统一弹出
  } finally {
    saving.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.title {
  margin: 0 0 4px;
  font-size: 18px;
}

.muted {
  color: #7a6a5d;
  font-size: 13px;
}

.tabs {
  margin-top: 12px;
}

.hint {
  margin-bottom: 16px;
}

.policy-form {
  max-width: 760px;
}

.field-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #7a6a5d;
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: 190px;
}
</style>
