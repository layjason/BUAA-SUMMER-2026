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
import { useAuthStore } from '@/stores/auth'
import zhCN from './locales/zh-CN.json'
import { devConfig, isDevEnabled } from '@/config/dev'

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

  // 数据模式：拦截 HTTP + 自动注入登录态
  if (isDevEnabled('mockApi')) {
    authStore.saveTokens('dev-mock-access-token', 'dev-mock-refresh-token')
    authStore.userId = '10001'
    authStore.userKind = devConfig.mockUserKind

    const responses = devConfig.mockData
    setMockHandler((method, path) => {
      const key = `${method} ${path}`
      const mock = responses[key]
      if (!mock) return null
      return Promise.resolve(mock as { code: number; message: string; data: unknown })
    })
  }

  setTokenGetter(() => authStore.getAccessToken())
  setRefreshTokenGetter(() => authStore.getRefreshToken())
  setBaseUrl('http://localhost:4010')
  setTokenSaver((access, refresh) => authStore.saveTokens(access, refresh))

  setOnTokenExpired(() => {
    if (isDevEnabled('mockApi')) return
    authStore.clearTokens()
    uni.redirectTo({ url: '/pages/login/index' })
  })

  // 应用启动时主动刷新 Token（数据模式下 mock token 无需刷新）
  if (authStore.isLoggedIn && !isDevEnabled('mockApi')) {
    authStore.tryRefreshToken()
  }

  return { app }
}
