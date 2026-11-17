<template>
  <AuthLayout>
    <h2 class="title">找回密码</h2>
    <p class="muted subtitle">核对注册信息后即可设置新密码</p>

    <el-alert
      class="tip"
      type="info"
      :closable="false"
      show-icon
      title="没有短信验证码，改用「你记得什么」来证明身份"
      description="请填写注册时的手机号与昵称；如果注册时选过所在小区，还要选对同一个小区。三项对不上会统一提示「信息不一致」，这是防止本页被用来试探别人的账号。"
    />

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" placeholder="注册时使用的手机号" clearable />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" :maxlength="LIMITS.NICKNAME_MAX" placeholder="注册时填写的昵称" clearable />
        </el-form-item>
        <el-form-item label="所在小区（注册时选过才需要选）" prop="communityId">
          <el-select v-model="form.communityId" placeholder="注册时没选过就留空" clearable style="width: 100%">
            <el-option
              v-for="c in communities"
              :key="c.id"
              :label="`${c.name}（${c.district}）`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password placeholder="6-20 位新密码" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入新密码" @keyup.enter="handleSubmit" />
        </el-form-item>

        <el-button type="primary" class="submit" :loading="loading" @click="handleSubmit">
          重置密码
        </el-button>
      </el-form>

      <div class="foot muted">
        想起来了？
        <router-link :to="{ name: 'login' }">返回登录</router-link>
      </div>
  </AuthLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AuthLayout from '@/layout/AuthLayout.vue'
import { resetPassword } from '@/api/auth'
import { listCommunities } from '@/api/community'
import { PHONE_PATTERN, LIMITS } from '@/utils/constants'

const router = useRouter()

const formRef = ref(null)
const loading = ref(false)
const communities = ref([])
const form = reactive({
  phone: '',
  nickname: '',
  communityId: null,
  newPassword: '',
  confirmPassword: ''
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
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: LIMITS.PASSWORD_MIN, max: LIMITS.PASSWORD_MAX, message: `密码长度 ${LIMITS.PASSWORD_MIN}-${LIMITS.PASSWORD_MAX} 位`, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.newPassword) callback(new Error('两次输入的密码不一致'))
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
    // 社区字典拉不到不影响重置：注册时没选过社区的账号本来就不需要这一项
  }
})

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await resetPassword({
      phone: form.phone,
      nickname: form.nickname,
      communityId: form.communityId || null,
      newPassword: form.newPassword
    })
    ElMessage.success('密码已重置，请用新密码登录')
    router.replace({ name: 'login' })
  } catch (e) {
    // 2001「信息不一致」/ 1006「账号已停用」等提示由 request.js 统一弹出
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

.subtitle { margin: 0 0 $space-4; }
.tip { margin-bottom: $space-4; }

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
