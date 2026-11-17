<template>
  <div class="wrap">
    <div class="card">
      <h2 class="title">管理端登录</h2>
      <p class="muted sub">仅管理员账号可进入（后端 hasRole('ADMIN') 校验）</p>

      <el-alert
        v-if="denied"
        type="warning"
        :closable="false"
        show-icon
        title="当前账号不是管理员，已退出"
        style="margin-bottom: 16px"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" placeholder="管理员手机号" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" @click="submit">登录</el-button>
      </el-form>

      <p class="muted hint">演示账号：13800000000 / admin123</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAdminStore } from '@/stores/admin'

const router = useRouter()
const route = useRoute()
const adminStore = useAdminStore()

const formRef = ref(null)
const loading = ref(false)
const denied = computed(() => route.query.denied === '1')
const form = reactive({ phone: '', password: '' })

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await adminStore.login({ phone: form.phone, password: form.password })
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' && redirect ? redirect : { name: 'dashboard' })
  } catch (e) {
    if (e?.code === 'NOT_ADMIN') ElMessage.error('该账号没有管理员权限')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1f2329;
}
.card {
  width: 400px;
  background: #fff;
  border-radius: 12px;
  padding: 32px 28px;
}
.title { margin: 0 0 4px; font-size: 20px; }
.sub { margin: 0 0 20px; }
.hint { margin-top: 16px; text-align: center; }
</style>