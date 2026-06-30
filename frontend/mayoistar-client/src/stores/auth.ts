/**
 * 认证状态管理
 *
 * 前置条件：应用启动时调用 initFromStorage 恢复持久化的认证信息
 * 后置条件：isLoggedIn 反映当前认证状态，token 可被 HTTP 客户端获取
 * 不变量：token 和 refreshToken 要么都为 null，要么都为有效字符串；
 *         userId 和 userKind 在 isLoggedIn 为 true 时不为 null
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api, TokenExpiredError } from '@/api'

const TOKEN_KEY = 'mayoistar_token'
const REFRESH_TOKEN_KEY = 'mayoistar_refresh_token'
const USER_ID_KEY = 'mayoistar_user_id'
const USER_KIND_KEY = 'mayoistar_user_kind'

/** 保留的注册表单数据，用于激活页返回注册时回填 */
export interface SavedRegisterForm {
  email: string
  password: string
  nickname: string
  type: 'personal' | 'merchant'
}

/**
 * 认证 Store
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const refreshToken = ref<string | null>(null)
  const userId = ref<string | null>(null)
  const userKind = ref<'personal' | 'merchant' | null>(null)
  const accountStatus = ref<string | null>(null)

  /** 注册后待激活的邮箱 */
  const pendingActivationEmail = ref<string | null>(null)
  /** 保留的注册表单数据，供激活页返回时回填 */
  const savedRegisterForm = ref<SavedRegisterForm | null>(null)

  const isLoggedIn = computed(() => token.value !== null && refreshToken.value !== null)

  /**
   * 从本地存储恢复认证信息
   *
   * 在应用启动时调用，恢复持久化的 Token 和用户基本信息。
   * 若 Token 已过期，由 tryRefreshToken 负责刷新。
   */
  function initFromStorage(): void {
    try {
      const savedToken = uni.getStorageSync(TOKEN_KEY)
      const savedRefreshToken = uni.getStorageSync(REFRESH_TOKEN_KEY)
      const savedUserId = uni.getStorageSync(USER_ID_KEY)
      const savedUserKind = uni.getStorageSync(USER_KIND_KEY)
      if (savedToken && savedRefreshToken) {
        token.value = savedToken
        refreshToken.value = savedRefreshToken
        if (savedUserId) userId.value = savedUserId
        if (savedUserKind) userKind.value = savedUserKind as 'personal' | 'merchant'
      }
    } catch {
      /* 存储不可用 */
    }
  }

  /**
   * 持久化认证信息到本地存储
   *
   * @param accessToken 访问令牌
   * @param refreshTokenVal 刷新令牌
   */
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

  /**
   * 清除所有认证状态
   *
   * 后置条件：token、refreshToken、userId、userKind、accountStatus 均为 null；
   *          本地存储中的认证信息被清除
   */
  function clearTokens(): void {
    token.value = null
    refreshToken.value = null
    userId.value = null
    userKind.value = null
    accountStatus.value = null
    pendingActivationEmail.value = null
    savedRegisterForm.value = null
    try {
      uni.removeStorageSync(TOKEN_KEY)
      uni.removeStorageSync(REFRESH_TOKEN_KEY)
      uni.removeStorageSync(USER_ID_KEY)
      uni.removeStorageSync(USER_KIND_KEY)
    } catch {
      /* 存储不可用 */
    }
  }

  /**
   * 邮箱密码登录
   *
   * 前置条件：email 和 password 已通过前端校验
   * 后置条件：成功时 token、refreshToken、userId、userKind、accountStatus 已设置并持久化；
   *          失败时抛出 BusinessError
   *
   * @param email 登录邮箱
   * @param password 明文密码
   * @returns 登录结果中的用户信息和令牌对
   */
  async function login(email: string, password: string): Promise<void> {
    const result = await api.post('/identity/auth/login', {
      body: { email, password },
    })

    saveTokens(result.tokens.accessToken, result.tokens.refreshToken)
    userId.value = result.userId
    // 客户端仅服务于个人和商家，管理员通过 Web 后台登录
    userKind.value = result.kind as 'personal' | 'merchant'
    accountStatus.value = result.accountStatus

    try {
      uni.setStorageSync(USER_ID_KEY, result.userId)
      uni.setStorageSync(USER_KIND_KEY, result.kind)
    } catch {
      /* 存储不可用 */
    }
    // 登录成功清除激活流程中的临时状态
    pendingActivationEmail.value = null
    savedRegisterForm.value = null
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
    pendingActivationEmail,
    savedRegisterForm,
    initFromStorage,
    saveTokens,
    clearTokens,
    login,
    tryRefreshToken,
    getAccessToken,
  }
})
