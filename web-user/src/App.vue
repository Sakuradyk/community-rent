<template>
  <!-- 规则 F5：组件样式由 unplugin-vue-components 按需注入；这里只提供中文语言包 -->
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>

<script setup>
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>