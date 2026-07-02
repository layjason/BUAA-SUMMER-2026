import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import App from './App.vue'
import {
  setTokenGetter,
  setBaseUrl,
  setTokenSaver,
  setOnTokenExpired,
  setRefreshTokenGetter,
  setMockHandler,
} from '@/api/client'
import { USE_MOCK, API_BASE_URL } from '@/api/config'
import { initMockDb } from '@/mock/database'
import { handleMockRequest } from '@/mock/mockServer'
import { setCurrentUserId } from '@/mock/workflow'
import { useAuthStore } from '@/stores/auth'
import zhCN from './locales/zh-CN.json'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': zhCN },
})

export function createApp() {
  const app = createSSRApp(App)
  const pinia = createPinia()
  app.use(pinia)
  app.use(i18n)

  const authStore = useAuthStore()
  authStore.initFromStorage()

  // ===== 有状态 Mock Server =====
  // USE_MOCK 为 true 时，所有 API 请求走内存态 mockDb（有状态 workflow）
  // USE_MOCK 为 false 时，走真实 HTTP 请求
  // 注意：不再自动登录，登录必须通过登录页完成
  if (USE_MOCK) {
    initMockDb()

    // 如果之前已登录（storage 中有 userId），同步到 workflow 的 currentUserId
    if (authStore.userId) {
      setCurrentUserId(Number(authStore.userId))
    }

    // 将 mockServer 的路由处理函数注入 client.ts 的 mockHandler
    setMockHandler((method, path, body) => handleMockRequest(method, path, body))
  }

  // ===== Token 与 API 基础配置 =====
  setTokenGetter(() => authStore.getAccessToken())
  setRefreshTokenGetter(() => authStore.getRefreshToken())
  setBaseUrl(API_BASE_URL)
  setTokenSaver((access, refresh) => authStore.saveTokens(access, refresh))

  setOnTokenExpired(() => {
    if (USE_MOCK) return
    authStore.clearTokens()
    uni.redirectTo({ url: '/pages/login/index' })
  })

  // 应用启动时主动刷新 Token（mock 模式下无需刷新）
  if (authStore.isLoggedIn && !USE_MOCK) {
    authStore.tryRefreshToken()
  }

  return { app }
}
