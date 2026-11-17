<template>
  <div class="auth-page">
    <div class="auth-card">
      <h2 class="title">登录邻里租</h2>
      <p class="muted subtitle">用手机号和密码登录</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" placeholder="请输入 11 位手机号" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" @keyup.enter="handleSubmit" />
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" @click="handleSubmit">
          登录
        </el-button>
      </el-form>

      <div class="foot muted">
        还没有账号？
        <router-link :to="{ name: 'register' }">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { PHONE_PATTERN, LIMITS } from '@/utils/constants'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ phone: '', password: '' })

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: PHONE_PATTERN, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: LIMITS.PASSWORD_MIN, max: LIMITS.PASSWORD_MAX, message: `密码长度 ${LIMITS.PASSWORD_MIN}-${LIMITS.PASSWORD_MAX} 位`, trigger: 'blur' }
  ]
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.login({ phone: form.phone, password: form.password })
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' && redirect ? redirect : { name: 'home' })
  } catch (e) {
    // 1005 / 1006 的提示已由 request.js 统一弹出
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.title { margin: 0 0 4px; font-size: 22px; }
.subtitle { margin: 0 0 24px; }
.submit { width: 100%; margin-top: 8px; }
.foot { margin-top: 20px; text-align: center; }
</style>