/**
 * 认证 Store 单元测试
 *
 * 测试 auth store 的所有核心方法。使用 vitest mock 模拟 API 客户端和 uni 存储。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

/* ---- Mock uni 全局对象 ---- */
const mockStorage: Record<string, string> = {}

vi.stubGlobal('uni', {
  getStorageSync: vi.fn((key: string) => mockStorage[key] ?? ''),
  setStorageSync: vi.fn((key: string, value: string) => {
    mockStorage[key] = value
  }),
  removeStorageSync: vi.fn((key: string) => {
    delete mockStorage[key]
  }),
  switchTab: vi.fn(),
  navigateTo: vi.fn(),
  navigateBack: vi.fn(),
  showToast: vi.fn(),
  request: vi.fn(),
  uploadFile: vi.fn(),
  onNetworkStatusChange: vi.fn(),
})

/* ---- Mock API 模块 ---- */
const mockApiPost = vi.fn()
const mockApiGet = vi.fn()

vi.mock('@/api', () => ({
  api: {
    post: (...args: unknown[]) => mockApiPost(...args),
    get: (...args: unknown[]) => mockApiGet(...args),
  },
  BusinessError: class BusinessError extends Error {
    code: number
    constructor(code: number, message: string) {
      super(message)
      this.code = code
      this.name = 'BusinessError'
    }
  },
  TokenExpiredError: class TokenExpiredError extends Error {
    constructor() {
      super('Token expired')
      this.name = 'TokenExpiredError'
    }
  },
}))

const { useAuthStore } = await import('@/stores/auth')

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    Object.keys(mockStorage).forEach((k) => delete mockStorage[k])
    vi.clearAllMocks()
  })

  describe('初始状态', () => {
    it('isLoggedIn 初始应为 false', () => {
      const store = useAuthStore()
      expect(store.isLoggedIn).toBe(false)
    })

    it('token 和 refreshToken 初始应为 null', () => {
      const store = useAuthStore()
      expect(store.token).toBeNull()
      expect(store.refreshToken).toBeNull()
      expect(store.userId).toBeNull()
      expect(store.userKind).toBeNull()
    })
  })

  describe('initFromStorage', () => {
    it('应从存储恢复 Token 和用户信息', () => {
      mockStorage['mayoistar_token'] = 'access-123'
      mockStorage['mayoistar_refresh_token'] = 'refresh-456'
      mockStorage['mayoistar_user_id'] = 'user-1'
      mockStorage['mayoistar_user_kind'] = 'personal'

      const store = useAuthStore()
      store.initFromStorage()

      expect(store.token).toBe('access-123')
      expect(store.refreshToken).toBe('refresh-456')
      expect(store.userId).toBe('user-1')
      expect(store.userKind).toBe('personal')
      expect(store.isLoggedIn).toBe(true)
    })

    it('Token 不完整时不应设置登录态', () => {
      mockStorage['mayoistar_token'] = 'access-123'
      // 不设置 refresh token

      const store = useAuthStore()
      store.initFromStorage()

      expect(store.token).toBeNull()
      expect(store.isLoggedIn).toBe(false)
    })
  })

  describe('login', () => {
    it('成功登录应保存 Token 和用户信息', async () => {
      mockApiPost.mockResolvedValueOnce({
        userId: 'user-1',
        kind: 'personal',
        accountStatus: 'active',
        tokens: {
          accessToken: 'access-new',
          refreshToken: 'refresh-new',
          expiresAt: '2025-01-01T00:00:00Z',
        },
      })

      const store = useAuthStore()
      await store.login('test@example.com', 'password123')

      expect(store.token).toBe('access-new')
      expect(store.refreshToken).toBe('refresh-new')
      expect(store.userId).toBe('user-1')
      expect(store.userKind).toBe('personal')
      expect(store.accountStatus).toBe('active')
      expect(store.isLoggedIn).toBe(true)

      expect(mockApiPost).toHaveBeenCalledWith('/identity/auth/login', {
        body: { email: 'test@example.com', password: 'password123' },
      })
    })

    it('登录失败应抛出 BusinessError', async () => {
      const bizErr = new Error('Invalid credentials') as Error & { code: number }
      ;(bizErr as unknown as Record<string, unknown>).code = 10003
      mockApiPost.mockRejectedValueOnce(bizErr)

      const store = useAuthStore()
      await expect(store.login('bad@example.com', 'wrong')).rejects.toThrow()

      expect(store.isLoggedIn).toBe(false)
    })
  })

  describe('clearTokens', () => {
    it('应清除所有认证状态和存储', () => {
      mockStorage['mayoistar_token'] = 'access-123'
      mockStorage['mayoistar_refresh_token'] = 'refresh-456'
      mockStorage['mayoistar_user_id'] = 'user-1'
      mockStorage['mayoistar_user_kind'] = 'personal'

      const store = useAuthStore()
      store.initFromStorage()

      store.clearTokens()

      expect(store.token).toBeNull()
      expect(store.refreshToken).toBeNull()
      expect(store.userId).toBeNull()
      expect(store.userKind).toBeNull()
      expect(store.accountStatus).toBeNull()
      expect(store.isLoggedIn).toBe(false)

      expect(mockStorage['mayoistar_token']).toBeUndefined()
      expect(mockStorage['mayoistar_refresh_token']).toBeUndefined()
    })
  })

  describe('tryRefreshToken', () => {
    it('成功刷新应更新 Token', async () => {
      mockStorage['mayoistar_token'] = 'access-old'
      mockStorage['mayoistar_refresh_token'] = 'refresh-old'

      mockApiPost.mockResolvedValueOnce({
        accessToken: 'access-new',
        refreshToken: 'refresh-new',
        expiresAt: '2025-01-01T00:00:00Z',
      })

      const store = useAuthStore()
      store.initFromStorage()

      const result = await store.tryRefreshToken()

      expect(result).toBe(true)
      expect(store.token).toBe('access-new')
      expect(store.refreshToken).toBe('refresh-new')
    })

    it('refreshToken 为 null 时应返回 false', async () => {
      const store = useAuthStore()
      const result = await store.tryRefreshToken()

      expect(result).toBe(false)
      expect(mockApiPost).not.toHaveBeenCalled()
    })
  })

  describe('getAccessToken', () => {
    it('已登录时应返回 token', () => {
      mockStorage['mayoistar_token'] = 'access-123'
      mockStorage['mayoistar_refresh_token'] = 'refresh-456'

      const store = useAuthStore()
      store.initFromStorage()

      expect(store.getAccessToken()).toBe('access-123')
    })

    it('未登录时应返回 null', () => {
      const store = useAuthStore()
      expect(store.getAccessToken()).toBeNull()
    })
  })
})
