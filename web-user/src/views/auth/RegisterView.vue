<template>
  <AuthLayout>
    <h2 class="title">注册邻里租</h2>
    <p class="muted subtitle">注册后即可发布闲置物品、预约邻居的物品</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" placeholder="请输入 11 位手机号" clearable />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" :maxlength="LIMITS.NICKNAME_MAX" placeholder="2-16 个字，注册后也可修改" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="6-20 位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再输入一次密码" />
        </el-form-item>
        <el-form-item label="所在小区（可选）" prop="communityId">
          <el-select v-model="form.communityId" placeholder="选择后列表会优先展示同小区物品" clearable style="width: 100%">
            <el-option
              v-for="c in communities"
              :key="c.id"
              :label="`${c.name}（${c.district}）`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>

        <el-button type="primary" class="submit" :loading="loading" @click="handleSubmit">注册并登录</el-button>
      </el-form>

      <div class="foot muted">
        已有账号？
        <router-link :to="{ name: 'login' }">去登录</router-link>
      </div>
  </AuthLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AuthLayout from '@/layout/AuthLayout.vue'
import { useUserStore } from '@/stores/user'
import { listCommunities } from '@/api/community'
import { PHONE_PATTERN, LIMITS } from '@/utils/constants'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const communities = ref([])
const form = reactive({
  phone: '',
  nickname: '',
  password: '',
  confirmPassword: '',
  communityId: null
})

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: PHONE_PATTERN, message: '手机号格式不正确', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: LIMITS.NICKNAME_MIN, max: LIMITS.NICKNAME_MAX, message: `昵称 ${LIMITS.NICKNAME_MIN}-${LIMITS.NICKNAME_MAX} 个字`, trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: LIMITS.PASSWORD_MIN, max: LIMITS.PASSWORD_MAX, message: `密码长度 ${LIMITS.PASSWORD_MIN}-${LIMITS.PASSWORD_MAX} 位`, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

onMounted(async () => {
  try {
    communities.value = await listCommunities()
  } catch (e) {
    // 字典拉不到不阻塞注册，社区是可选项
  }
})

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.register({
      phone: form.phone,
      password: form.password,
      nickname: form.nickname,
      // 空值传 null，让后端按"不填"处理，不要传空字符串
      communityId: form.communityId || null
    })
    ElMessage.success('注册成功，已自动登录')
    router.replace({ name: 'home' })
  } catch (e) {
    // 手机号已注册等错误已由 request.js 提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.title {
  margin: 0 0 $space-1;
  font-size: $font-size-xl;
  font-weight: $font-weight-bold;
}

.subtitle { margin: 0 0 $space-6; }

.submit {
  width: 100%;
  margin-top: $space-2;
}

.foot {
  margin-top: $space-6;
  text-align: center;
  font-size: $font-size-sm;
}
</style>