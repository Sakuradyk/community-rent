<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createItem, getItem, updateItem } from '@/api/item'
import { listCategories } from '@/api/category'
import { listCommunities } from '@/api/community'
import { uploadImage } from '@/api/file'
import { useUserStore } from '@/stores/user'
import { LIMITS, groupCategories } from '@/utils/constants'
import { resolveImageUrl } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const editId = computed(() => route.query.edit || null)
const isEdit = computed(() => Boolean(editId.value))

const formRef = ref(null)
const submitting = ref(false)
const loading = ref(false)
const uploading = ref(false)
const categories = ref([])
const communities = ref([])

const form = reactive({
  title: '',
  // 一级类目只用于筛选二级下拉，不提交给后端；但它是"二级必选"的前置，所以必须进 form 才能被 el-form 校验
  parentCategoryId: null,
  categoryId: null,
  communityId: null,
  description: '',
  dailyPrice: 10,
  deposit: 100,
  overdueRate: 0.5,
  imageUrls: []
})

const childCategories = computed(() => {
  const group = categories.value.find((g) => String(g.id) === String(form.parentCategoryId))
  return group ? group.children : []
})

// 发布页只列二级类目做校验提示：后端 ItemService 会拒绝一级类目（2001）
const rules = {
  title: [
    { required: true, message: '请填写物品名称', trigger: 'blur' },
    { max: 64, message: '名称最多 64 个字', trigger: 'blur' }
  ],
  parentCategoryId: [{ required: true, message: '请选择一级类目', trigger: 'change' }],
  categoryId: [{ required: true, message: '请选择二级类目', trigger: 'change' }],
  communityId: [{ required: true, message: '请选择所在小区', trigger: 'change' }],
  dailyPrice: [{ required: true, message: '请填写日租金', trigger: 'blur' }],
  deposit: [{ required: true, message: '请填写押金', trigger: 'blur' }],
  overdueRate: [{ required: true, message: '请填写逾期费率', trigger: 'blur' }],
  description: [{ max: 1000, message: '描述最多 1000 个字', trigger: 'blur' }]
}

function beforeUpload(file) {
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  if (!LIMITS.IMAGE_ALLOWED_EXT.includes(ext)) {
    ElMessage.error(`只支持 ${LIMITS.IMAGE_ALLOWED_EXT.join(' / ')} 格式`)
    return false
  }
  if (file.size > LIMITS.IMAGE_MAX_MB * 1024 * 1024) {
    ElMessage.error(`单张图片不能超过 ${LIMITS.IMAGE_MAX_MB}MB`)
    return false
  }
  if (form.imageUrls.length >= LIMITS.IMAGE_MAX_COUNT) {
    ElMessage.error(`最多上传 ${LIMITS.IMAGE_MAX_COUNT} 张图片`)
    return false
  }
  return true
}

/**
 * 自定义上传：先走 /api/files/upload 拿到相对路径，再把路径放进表单一起提交。
 * 这样"发布物品"与"改头像"共用同一套上传逻辑，图片不随表单 multipart 一起走，
 * 后端接口也就不必同时处理 JSON 与 multipart 两种入参。
 */
async function handleUpload(options) {
  uploading.value = true
  try {
    const { url } = await uploadImage(options.file)
    form.imageUrls.push(url)
    options.onSuccess?.({ url })
  } catch (e) {
    options.onError?.(e)
  } finally {
    uploading.value = false
  }
}

function removeImage(index) {
  form.imageUrls.splice(index, 1)
}

async function loadEditData() {
  loading.value = true
  try {
    const detail = await getItem(editId.value)
    form.title = detail.title
    form.categoryId = detail.categoryId
    form.communityId = detail.communityId
    form.description = detail.description || ''
    form.dailyPrice = Number(detail.dailyPrice)
    form.deposit = Number(detail.deposit)
    form.overdueRate = Number(detail.overdueRate)
    form.imageUrls = (detail.images || []).map((img) => img.url)
    const group = categories.value.find((g) =>
      g.children.some((child) => String(child.id) === String(detail.categoryId))
    )
    form.parentCategoryId = group ? group.id : null
  } catch (e) {
    // 提示已由 request.js 统一弹出
  } finally {
    loading.value = false
  }
}

function onParentChange() {
  // 换了一级类目，原来的二级类目必然失效
  form.categoryId = null
}
async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (form.imageUrls.length === 0) {
    ElMessage.error('至少上传 1 张图片（最多 5 张）')
    return
  }
  if (form.deposit < 0) {
    ElMessage.error('押金不能为负数')
    return
  }

  const payload = {
    title: form.title.trim(),
    categoryId: form.categoryId,
    description: form.description.trim(),
    communityId: form.communityId,
    dailyPrice: form.dailyPrice,
    deposit: form.deposit,
    overdueRate: form.overdueRate,
    imageUrls: [...form.imageUrls]
  }

  submitting.value = true
  try {
    if (isEdit.value) {
      await updateItem(editId.value, payload)
      ElMessage.success('已提交修改，物品回到「待审核」，管理员重新审核后上架')
    } else {
      await createItem(payload)
      ElMessage.success('发布成功，等待管理员审核')
    }
    router.push({ name: 'item-mine' })
  } catch (e) {
    // 4001 价格越界、2001 类目/社区非法等提示已由 request.js 统一弹出
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  const [categoryList, communityList] = await Promise.all([
    listCategories().catch(() => []),
    listCommunities().catch(() => [])
  ])
  categories.value = groupCategories(categoryList)
  communities.value = communityList
  if (userStore.profile?.communityId) {
    form.communityId = userStore.profile.communityId
  }
  if (isEdit.value) {
    await loadEditData()
  }
})
</script>

<template>
  <div class="page-container narrow" v-loading="loading">
    <h1>{{ isEdit ? '编辑物品' : '发布物品' }}</h1>
    <p class="muted intro">
      提交后进入「待审核」，管理员通过后才会上架。价格三件套（日租金 / 押金 / 逾期费率）会随订单快照，
      之后改价不影响已下的订单。
    </p>

    <el-alert
      v-if="isEdit"
      type="warning"
      :closable="false"
      show-icon
      title="保存后会回到「待审核」，并清空上次的审核意见"
      class="tip"
    />

    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" class="card form">
      <el-form-item label="物品名称" prop="title">
        <el-input v-model="form.title" maxlength="64" show-word-limit placeholder="例如：佳能 EOS 200D 单反机身" />
      </el-form-item>

      <!-- 一级类目只用于筛出二级下拉；后端只接受二级类目 id，所以两个都必填但只有 categoryId 会提交 -->
      <el-form-item label="一级类目" prop="parentCategoryId">
        <el-select v-model="form.parentCategoryId" placeholder="先选一级类目" class="w-320" @change="onParentChange">
          <el-option v-for="g in categories" :key="g.id" :label="g.name" :value="g.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="二级类目" prop="categoryId">
        <el-select
          v-model="form.categoryId"
          :placeholder="form.parentCategoryId ? '选择二级类目' : '请先选一级类目'"
          class="w-320"
          :disabled="!form.parentCategoryId"
        >
          <el-option v-for="c in childCategories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <span v-if="form.parentCategoryId && childCategories.length === 0" class="muted hint">
          该一级类目下暂无二级类目，请联系管理员补充
        </span>
      </el-form-item>

      <el-form-item label="所在小区" prop="communityId">
        <el-select v-model="form.communityId" placeholder="从字典中选择" class="w-320">
          <el-option v-for="c in communities" :key="c.id" :label="`${c.name}（${c.district}）`" :value="c.id" />
        </el-select>
        <span class="muted hint">列表页会优先展示同小区物品</span>
      </el-form-item>

      <el-form-item label="图片" required>
        <div class="uploader">
          <div v-for="(url, index) in form.imageUrls" :key="url" class="pic">
            <img :src="resolveImageUrl(url)" alt="物品图片" />
            <button type="button" class="remove" @click="removeImage(index)">×</button>
          </div>
          <el-upload
            v-if="form.imageUrls.length < LIMITS.IMAGE_MAX_COUNT"
            :show-file-list="false"
            :http-request="handleUpload"
            :before-upload="beforeUpload"
            :disabled="uploading"
          >
            <div class="upload-add" v-loading="uploading">
              <span>+</span>
              <small>上传图片</small>
            </div>
          </el-upload>
        </div>
        <p class="muted uploader-hint">
          1–{{ LIMITS.IMAGE_MAX_COUNT }} 张，单张不超过 {{ LIMITS.IMAGE_MAX_MB }}MB，支持
          {{ LIMITS.IMAGE_ALLOWED_EXT.join(' / ') }}；第一张作为封面。
        </p>
      </el-form-item>

      <el-form-item label="日租金" prop="dailyPrice">
        <el-input-number v-model="form.dailyPrice" :min="0.01" :precision="2" :step="1" />
        <span class="muted hint">元/天，需落在 fee_policy 的日租上下限内，越界后端返回 4001</span>
      </el-form-item>

      <el-form-item label="押金" prop="deposit">
        <el-input-number v-model="form.deposit" :min="0" :precision="2" :step="10" />
        <span class="muted hint">元，需在「日租金 × 押金倍数」的策略区间内</span>
      </el-form-item>

      <el-form-item label="逾期费率" prop="overdueRate">
        <el-input-number v-model="form.overdueRate" :min="0" :precision="2" :step="0.1" />
        <span class="muted hint">倍，逾期每天按「日租金 × 该费率」计费（前 3 天另有折扣策略）</span>
      </el-form-item>

      <el-form-item label="物品描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="4"
          maxlength="1000"
          show-word-limit
          placeholder="使用注意、配件清单、取还方式等"
        />
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="submit">
          {{ isEdit ? '保存修改' : '提交审核' }}
        </el-button>
        <el-button @click="router.back()">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.narrow {
  max-width: 760px;
}

h1 {
  margin: 0 0 $space-1;
  font-size: $font-size-xl;
}

.intro {
  margin: 0 0 $space-4;
  font-size: $font-size-sm;
}

.tip {
  margin-bottom: $space-4;
}

.form {
  padding: $space-6;
}

.w-320 {
  width: 320px;
}

.hint {
  margin-left: $space-2;
  font-size: $font-size-xs;
}

.uploader {
  display: flex;
  flex-wrap: wrap;
  gap: $space-2;
}

.pic {
  position: relative;
  width: 96px;
  height: 96px;
  border-radius: $radius-sm;
  overflow: hidden;
  border: 1px solid $color-border;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }

  .remove {
    position: absolute;
    top: 2px;
    right: 2px;
    width: 20px;
    height: 20px;
    border: none;
    border-radius: 50%;
    background: rgba(0, 0, 0, 0.55);
    color: #fff;
    cursor: pointer;
    line-height: 1;
  }
}

.upload-add {
  width: 96px;
  height: 96px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  border: 1px dashed $color-border-strong;
  border-radius: $radius-sm;
  color: $color-text-secondary;
  cursor: pointer;

  span {
    font-size: 20px;
    line-height: 1;
  }

  small {
    font-size: $font-size-xs;
  }
}

.uploader-hint {
  margin: $space-2 0 0;
  font-size: $font-size-xs;
}
</style>
