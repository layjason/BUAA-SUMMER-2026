/**
 * 认证状态管理
 *
 * 前置条件：应用启动时调用 initFromStorage 恢复持久化的 Token
 * 后置条件：isLoggedIn 反映当前认证状态，token 可被 HTTP 客户端获取
 * 不变量：token 和 refreshToken 要么都为 null，要么都为有效字符串
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api, TokenExpiredError } from '@/api'

const TOKEN_KEY = 'mayoistar_token'
const REFRESH_TOKEN_KEY = 'mayoistar_refresh_token'

/**
 * 认证 Store
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const refreshToken = ref<string | null>(null)
  const userId = ref<string | null>(null)
  const userKind = ref<'personal' | 'merchant' | null>(null)
  const accountStatus = ref<string | null>(null)

  const isLoggedIn = computed(() => token.value !== null && refreshToken.value !== null)

  function initFromStorage(): void {
    try {
      const savedToken = uni.getStorageSync(TOKEN_KEY)
      const savedRefreshToken = uni.getStorageSync(REFRESH_TOKEN_KEY)
      if (savedToken && savedRefreshToken) {
        token.value = savedToken
        refreshToken.value = savedRefreshToken
      }
    } catch {
      /* 存储不可用 */
    }
  }

  function saveTokens(accessToken: string, refreshTokenVal: string): void {
    token.value = accessToken
    refreshToken.value = refreshTokenVal
    try {
      uni.setStorageSync(TOKEN_KEY, accessToken)
      uni.setStorageSync(REFRESH_TOKEN_KEY, refreshTokenVal)
    } catch {
      /* 存储不可用 */
    }
  }

  function clearTokens(): void {
    token.value = null
    refreshToken.value = null
    userId.value = null
    userKind.value = null
    accountStatus.value = null
    try {
      uni.removeStorageSync(TOKEN_KEY)
      uni.removeStorageSync(REFRESH_TOKEN_KEY)
    } catch {
      /* 存储不可用 */
    }
  }

  /**
   * 尝试刷新 Token
   *
   * 前置条件：refreshToken 不为 null
   * 后置条件：成功则更新 Token，失败则清除认证状态
   */
  async function tryRefreshToken(): Promise<boolean> {
    if (!refreshToken.value) return false
    try {
      const result = await api.post('/identity/auth/refresh', {
        body: { refreshToken: refreshToken.value },
      })
      saveTokens(result.accessToken, result.refreshToken)
      return true
    } catch (error) {
      if (error instanceof TokenExpiredError) {
        clearTokens()
      }
      return false
    }
  }

  function getAccessToken(): string | null {
    return token.value
  }

  return {
    token,
    refreshToken,
    userId,
    userKind,
    accountStatus,
    isLoggedIn,
    initFromStorage,
    saveTokens,
    clearTokens,
    tryRefreshToken,
    getAccessToken,
  }
})
