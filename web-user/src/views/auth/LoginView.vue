<template>
  <AuthLayout>
    <h2 class="title">登录</h2>
    <p class="muted subtitle">用手机号和密码进入邻里租</p>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @submit.prevent>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="form.phone" maxlength="11" placeholder="请输入 11 位手机号" clearable />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" @keyup.enter="handleSubmit" />
      </el-form-item>

      <div class="options">
        <el-checkbox v-model="remember">记住密码</el-checkbox>
        <router-link class="forgot" :to="{ name: 'forgot' }">忘记密码？</router-link>
      </div>

      <el-button type="primary" class="submit" :loading="loading" @click="handleSubmit">
        登录
      </el-button>
    </el-form>

    <div class="foot muted">
      还没有账号？
      <router-link :to="{ name: 'register' }">立即注册</router-link>
    </div>
  </AuthLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import AuthLayout from '@/layout/AuthLayout.vue'
import { useUserStore } from '@/stores/user'
import { PHONE_PATTERN, LIMITS } from '@/utils/constants'
import { saveRemember, loadRemember, clearRemember } from '@/utils/remember'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const remember = ref(false)
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

// 上次勾过"记住密码"就把手机号和密码回填，用户改一个字就能登录
onMounted(() => {
  const saved = loadRemember()
  if (saved) {
    form.phone = saved.phone
    form.password = saved.password
    remember.value = true
  }
})

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.login({ phone: form.phone, password: form.password })
    // 只有登录成功才落盘：密码打错时不该把错密码记下来
    if (remember.value) saveRemember(form.phone, form.password)
    else clearRemember()
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
@use '@/assets/styles/variables.scss' as *;

.title {
  margin: 0 0 $space-1;
  font-size: $font-size-xl;
  font-weight: $font-weight-bold;
}

.subtitle {
  margin: 0 0 $space-6;
}

.options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -$space-1 0 $space-3;
}

.forgot {
  font-size: $font-size-xs;
}

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
