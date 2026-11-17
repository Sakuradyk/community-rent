import { createApp } from 'vue'
import { createPinia } from 'pinia'
// 函数式 API（ElMessage/ElMessageBox）不走模板，按需引入插件不会替它们注入样式，
// 显式 import 的只是个 JS 函数 → 必须手工引入对应样式，否则消息渲染在文档流末尾（看不见）。
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import App from './App.vue'
import router from './router'
import './assets/styles/global.scss'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')