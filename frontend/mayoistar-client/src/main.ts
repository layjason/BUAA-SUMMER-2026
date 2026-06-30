import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import App from './App.vue'
import { setTokenGetter, setBaseUrl, setTokenSaver, setOnTokenExpired } from '@/api/client'
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
  setTokenGetter(() => authStore.getAccessToken())
  setBaseUrl('http://localhost:4010')
  setTokenSaver((access, refresh) => authStore.saveTokens(access, refresh))
  setOnTokenExpired(() => {
    authStore.clearTokens()
  })

  return { app }
}
