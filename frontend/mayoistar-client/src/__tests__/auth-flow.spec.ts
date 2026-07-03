/**
 * 认证流程集成测试
 *
 * 使用状态化的 Mock 后端模拟完整认证链路：
 *   注册 → 激活 → 登录 → 错误处理 → 登出
 *
 * 不依赖真实服务，所有 API 调用由内存 Mock 处理。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

/* ==========================================================================
  Mock uni 全局对象
  ========================================================================== */
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

/* ==========================================================================
  状态化 Mock 后端
  ========================================================================== */

/** 已注册用户表 (email → { password, nickname, kind, userId }) */
interface StoredUser {
  password: string
  nickname?: string
  kind: 'personal' | 'merchant'
  userId: string
}

let userCount = 0
const users = new Map<string, StoredUser>()
/** 已使用的昵称集合（个人/商家共享唯一性约束） */
const usedNicknames = new Set<string>()

/** 激活 token 表（token → { email, consumed }），consumed=true 表示已被 Web 或 APP 消费 */
interface ActivationRecord {
  email: string
  consumed: boolean
}
let activationTokenCounter = 0
const activationTokens = new Map<string, ActivationRecord>()

function nextUserId(): string {
  return `user-${String(++userCount).padStart(4, '0')}`
}

function nextAccessToken(): string {
  return `access-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function nextRefreshToken(): string {
  return `refresh-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function nextActivationToken(): string {
  return `activate-${String(++activationTokenCounter).padStart(4, '0')}-${Math.random()
    .toString(36)
    .slice(2, 8)}`
}

/**
 * 模拟的后端业务逻辑 — GET 请求
 */
function mockGet(path: string, options?: { path?: unknown; query?: Record<string, unknown> }) {
  switch (path) {
    /* ---- 昵称唯一性校验 ---- */
    case '/identity/nicknames/availability': {
      const nickname = (options?.query?.nickname as string) ?? ''
      if (!nickname) {
        return { nickname: '', available: false }
      }
      return { nickname, available: !usedNicknames.has(nickname) }
    }
    default:
      throw new Error(`未实现的 Mock 端点: ${path}`)
  }
}

/**
 * 模拟的后端业务逻辑 — POST 请求
 */
function mockPost(path: string, options?: { path?: unknown; body?: unknown }) {
  const body = (options as { body?: Record<string, unknown> } | undefined)?.body

  switch (path) {
    /* ---- 登录 ---- */
    case '/identity/auth/login': {
      const { email, password } = body as { email: string; password: string }
      if (!email || !password) {
        throw createBusinessError(400, '请求参数不完整')
      }

      const user = users.get(email)
      if (!user) {
        throw createBusinessError(10011, '该邮箱未注册')
      }
      if (user.password !== password) {
        throw createBusinessError(10003, '邮箱或密码错误')
      }

      return {
        userId: user.userId,
        kind: user.kind,
        accountStatus: 'active',
        tokens: {
          accessToken: nextAccessToken(),
          refreshToken: nextRefreshToken(),
          expiresAt: new Date(Date.now() + 86400000).toISOString(),
        },
      }
    }

    /* ---- 个人注册 ---- */
    case '/identity/auth/register/personal': {
      const { email, password, nickname } = body as {
        email: string
        password: string
        nickname: string
      }
      if (!email || !password || !nickname) {
        throw createBusinessError(400, '请求参数不完整')
      }
      if (users.has(email)) {
        throw createBusinessError(10001, '该邮箱已被注册')
      }
      if (usedNicknames.has(nickname)) {
        throw createBusinessError(10002, '昵称已被占用')
      }

      users.set(email, {
        password,
        nickname,
        kind: 'personal',
        userId: nextUserId(),
      })
      usedNicknames.add(nickname)
      // 模拟后端发送激活邮件（清除旧 token 生成新 token）
      for (const [oldToken, rec] of activationTokens) {
        if (rec.email === email) activationTokens.delete(oldToken)
      }
      activationTokens.set(nextActivationToken(), { email, consumed: false })
      return {}
    }

    /* ---- 商家注册 ---- */
    case '/identity/auth/register/merchant': {
      const { email, password } = body as { email: string; password: string }
      if (!email || !password) {
        throw createBusinessError(400, '请求参数不完整')
      }
      if (users.has(email)) {
        throw createBusinessError(10001, '该邮箱已被注册')
      }

      users.set(email, {
        password,
        kind: 'merchant',
        userId: nextUserId(),
      })
      // 模拟后端发送激活邮件
      for (const [oldToken, rec] of activationTokens) {
        if (rec.email === email) activationTokens.delete(oldToken)
      }
      activationTokens.set(nextActivationToken(), { email, consumed: false })
      return {}
    }

    /* ---- 登出 ---- */
    case '/identity/auth/logout': {
      return {}
    }

    /* ---- 账号激活 ---- */
    case '/identity/auth/activate': {
      const { token } = body as { token: string }
      if (!token) {
        throw createBusinessError(400, '请求参数不完整')
      }

      const record = activationTokens.get(token)
      if (!record) {
        throw createBusinessError(10006, '激活链接无效或已过期')
      }
      if (record.consumed) {
        throw createBusinessError(10012, '账号已激活')
      }

      record.consumed = true
      return {}
    }

    /* ---- 重发激活邮件 ---- */
    case '/identity/auth/activation-email': {
      const { email } = body as { email: string }
      if (!email) {
        throw createBusinessError(400, '请求参数不完整')
      }
      if (!users.has(email)) {
        throw createBusinessError(10011, '该邮箱未注册')
      }

      // 检查是否已有已消费的 token（已激活）
      let alreadyActive = false
      for (const [, record] of activationTokens) {
        if (record.email === email && record.consumed) {
          alreadyActive = true
          break
        }
      }
      if (alreadyActive) {
        throw createBusinessError(10012, '账号已激活')
      }

      // 移除该邮箱已有的未消费 token，生成新 token
      for (const [oldToken, record] of activationTokens) {
        if (record.email === email && !record.consumed) {
          activationTokens.delete(oldToken)
        }
      }
      const newToken = nextActivationToken()
      activationTokens.set(newToken, { email, consumed: false })
      return {}
    }

    /* ---- 刷新 Token ---- */
    case '/identity/auth/refresh': {
      const { refreshToken } = body as { refreshToken: string }
      if (!refreshToken) {
        throw createBusinessError(10007, '登录已过期，请重新登录')
      }
      return {
        accessToken: nextAccessToken(),
        refreshToken: nextRefreshToken(),
        expiresAt: new Date(Date.now() + 86400000).toISOString(),
      }
    }

    default:
      throw new Error(`未实现的 Mock 端点: ${path}`)
  }
}

function createBusinessError(code: number, message: string): Error & { code: number } {
  const err = new Error(message) as Error & { code: number }
  err.code = code
  err.name = 'BusinessError'
  return err
}

/* ==========================================================================
  Mock API 模块
  ========================================================================== */

vi.mock('@/api', () => ({
  api: { post: mockPost, get: mockGet },
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

/* ==========================================================================
  集成测试
  ========================================================================== */

const TEST_EMAIL = 'alice@example.com'
const TEST_PASSWORD = 'correct-password'
const TEST_NICKNAME = 'Alice'
const WRONG_PASSWORD = 'wrong-password'

describe('认证流程集成测试', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    users.clear()
    usedNicknames.clear()
    activationTokens.clear()
    activationTokenCounter = 0
    userCount = 0
    Object.keys(mockStorage).forEach((k) => delete mockStorage[k])
    vi.clearAllMocks()
  })

  /* ---- 场景 1: 未注册邮箱登录应失败 ---- */
  it('未注册邮箱登录应返回"该邮箱未注册"', async () => {
    const store = useAuthStore()

    let caughtCode = 0
    try {
      await store.login(TEST_EMAIL, TEST_PASSWORD)
    } catch (error) {
      caughtCode = (error as { code: number }).code
    }

    expect(caughtCode).toBe(10011)
    expect(store.isLoggedIn).toBe(false)
    expect(store.token).toBeNull()
  })

  /* ---- 场景 2: 个人用户注册成功 ---- */
  it('个人用户注册应成功写入用户表', async () => {
    const apiModule = await import('@/api')
    await apiModule.api.post('/identity/auth/register/personal', {
      body: { email: TEST_EMAIL, password: TEST_PASSWORD, nickname: TEST_NICKNAME },
    })

    expect(users.has(TEST_EMAIL)).toBe(true)
    const stored = users.get(TEST_EMAIL)
    expect(stored!.password).toBe(TEST_PASSWORD)
    expect(stored!.nickname).toBe(TEST_NICKNAME)
    expect(stored!.kind).toBe('personal')
    expect(stored!.userId).toBeTruthy()
  })

  /* ---- 场景 3: 重复注册应失败 ---- */
  it('重复注册同一邮箱应返回"该邮箱已被注册"', async () => {
    const apiModule = await import('@/api')

    await apiModule.api.post('/identity/auth/register/personal', {
      body: { email: TEST_EMAIL, password: TEST_PASSWORD, nickname: TEST_NICKNAME },
    })

    let caughtCode = 0
    try {
      await apiModule.api.post('/identity/auth/register/personal', {
        body: { email: TEST_EMAIL, password: 'another', nickname: 'Bob' },
      })
    } catch (error) {
      caughtCode = (error as { code: number }).code
    }

    expect(caughtCode).toBe(10001)
  })

  /* ---- 场景 4: 注册后使用错误密码登录 ---- */
  it('注册后使用错误密码登录应返回"邮箱或密码错误"', async () => {
    const apiModule = await import('@/api')

    await apiModule.api.post('/identity/auth/register/personal', {
      body: { email: TEST_EMAIL, password: TEST_PASSWORD, nickname: TEST_NICKNAME },
    })

    const store = useAuthStore()
    let caughtCode = 0
    try {
      await store.login(TEST_EMAIL, WRONG_PASSWORD)
    } catch (error) {
      caughtCode = (error as { code: number }).code
    }

    expect(caughtCode).toBe(10003)
    expect(store.isLoggedIn).toBe(false)
  })

  /* ---- 场景 5: 注册后使用正确密码登录 ---- */
  it('注册后使用正确密码登录应成功并持有 Token', async () => {
    const apiModule = await import('@/api')

    await apiModule.api.post('/identity/auth/register/personal', {
      body: { email: TEST_EMAIL, password: TEST_PASSWORD, nickname: TEST_NICKNAME },
    })

    const store = useAuthStore()
    await store.login(TEST_EMAIL, TEST_PASSWORD)

    expect(store.isLoggedIn).toBe(true)
    expect(store.token).toBeTruthy()
    expect(store.refreshToken).toBeTruthy()
    expect(store.userId).toBeTruthy()
    expect(store.userKind).toBe('personal')
    expect(store.accountStatus).toBe('active')

    expect(mockStorage['mayoistar_token']).toBeTruthy()
    expect(mockStorage['mayoistar_refresh_token']).toBeTruthy()
    expect(mockStorage['mayoistar_user_id']).toBeTruthy()
    expect(mockStorage['mayoistar_user_kind']).toBe('personal')
  })

  /* ---- 场景 6: 完整闭环 ---- */
  it('完整闭环：注册 → 错误密码登录 → 正确登录 → 登出 → 重新登录', async () => {
    const apiModule = await import('@/api')

    // 1) 注册
    await apiModule.api.post('/identity/auth/register/personal', {
      body: { email: TEST_EMAIL, password: TEST_PASSWORD, nickname: TEST_NICKNAME },
    })

    // 2) 错误密码登录
    let store = useAuthStore()
    await expect(store.login(TEST_EMAIL, WRONG_PASSWORD)).rejects.toThrow()
    expect(store.isLoggedIn).toBe(false)

    // 3) 正确密码登录
    await store.login(TEST_EMAIL, TEST_PASSWORD)
    expect(store.isLoggedIn).toBe(true)
    const firstToken = store.token

    // 4) 登出
    store.clearTokens()
    expect(store.isLoggedIn).toBe(false)
    expect(store.token).toBeNull()
    expect(store.userId).toBeNull()

    // 5) 重新登录
    await store.login(TEST_EMAIL, TEST_PASSWORD)
    expect(store.isLoggedIn).toBe(true)
    expect(store.token).not.toBe(firstToken) // Token 重新签发

    // 6) 商家注册并登录
    const merchantEmail = 'shop@example.com'
    const merchantPassword = 'shop-password'
    await apiModule.api.post('/identity/auth/register/merchant', {
      body: { email: merchantEmail, password: merchantPassword, nickname: '测试商家' },
    })

    // 重新 init，模拟新 store 实例
    setActivePinia(createPinia())
    store = useAuthStore()
    await store.login(merchantEmail, merchantPassword)
    expect(store.userKind).toBe('merchant')
  })

  /* ---- 场景 7: 昵称可用性校验 — 未占用 ---- */
  it('未占用的昵称应返回 available=true', async () => {
    const apiModule = await import('@/api')
    const result = await apiModule.api.get('/identity/nicknames/availability', {
      query: { nickname: 'UniqueNick' },
    })

    expect(result).toEqual({ nickname: 'UniqueNick', available: true })
  })

  /* ---- 场景 8: 昵称可用性校验 — 已占用 ---- */
  it('已占用的昵称应返回 available=false', async () => {
    const apiModule = await import('@/api')

    await apiModule.api.post('/identity/auth/register/personal', {
      body: { email: 'user1@test.com', password: 'pass123', nickname: 'Taken' },
    })

    const result = await apiModule.api.get('/identity/nicknames/availability', {
      query: { nickname: 'Taken' },
    })

    expect(result).toEqual({ nickname: 'Taken', available: false })
  })

  /* ---- 场景 9: 用已被占用的昵称注册应失败 ---- */
  it('用已被占用的昵称注册应返回"昵称已被占用"', async () => {
    const apiModule = await import('@/api')

    await apiModule.api.post('/identity/auth/register/personal', {
      body: { email: 'first@test.com', password: 'pass1', nickname: 'Alice' },
    })

    let caughtCode = 0
    try {
      await apiModule.api.post('/identity/auth/register/personal', {
        body: { email: 'second@test.com', password: 'pass2', nickname: 'Alice' },
      })
    } catch (error) {
      caughtCode = (error as { code: number }).code
    }

    expect(caughtCode).toBe(10002)
  })

  /* ---- 场景 10: 空昵称查询应返回不可用 ---- */
  it('空昵称查询应返回 available=false', async () => {
    const apiModule = await import('@/api')
    const result = await apiModule.api.get('/identity/nicknames/availability', {
      query: { nickname: '' },
    })

    expect(result).toEqual({ nickname: '', available: false })
  })

  /* ==========================================================================
    场景 11-18: 激活流程
    ========================================================================== */

  /**
   * 获取指定邮箱对应的未消费激活 token
   */
  function getActivationToken(email: string): string | null {
    for (const [token, record] of activationTokens) {
      if (record.email === email && !record.consumed) return token
    }
    return null
  }

  /* ---- 场景 11: 使用有效 token 激活成功 ---- */
  it('使用有效激活 token 应成功', async () => {
    const apiModule = await import('@/api')

    await apiModule.api.post('/identity/auth/register/personal', {
      body: { email: TEST_EMAIL, password: TEST_PASSWORD, nickname: TEST_NICKNAME },
    })

    const token = getActivationToken(TEST_EMAIL)
    expect(token).toBeTruthy()

    await apiModule.api.post('/identity/auth/activate', {
      body: { token: token! },
    })

    const record = activationTokens.get(token!)
    expect(record!.consumed).toBe(true)
  })

  /* ---- 场景 12: 使用无效 token 应失败 ---- */
  it('使用不存在的激活 token 应返回 10006', async () => {
    const apiModule = await import('@/api')

    let caughtCode = 0
    try {
      await apiModule.api.post('/identity/auth/activate', {
        body: { token: 'nonexistent-token' },
      })
    } catch (error) {
      caughtCode = (error as { code: number }).code
    }

    expect(caughtCode).toBe(10006)
  })

  /* ---- 场景 13: 重复激活已消费 token 应返回 10012 ---- */
  it('重复激活已消费的 token 应返回 10012', async () => {
    const apiModule = await import('@/api')

    await apiModule.api.post('/identity/auth/register/personal', {
      body: { email: TEST_EMAIL, password: TEST_PASSWORD, nickname: TEST_NICKNAME },
    })

    const token = getActivationToken(TEST_EMAIL)
    await apiModule.api.post('/identity/auth/activate', { body: { token: token! } })

    // 第二次激活同一 token
    let caughtCode = 0
    try {
      await apiModule.api.post('/identity/auth/activate', {
        body: { token: token! },
      })
    } catch (error) {
      caughtCode = (error as { code: number }).code
    }

    expect(caughtCode).toBe(10012)
  })

  /* ---- 场景 14: 重发激活邮件成功 ---- */
  it('重发激活邮件应为已注册邮箱生成新 token', async () => {
    const apiModule = await import('@/api')

    await apiModule.api.post('/identity/auth/register/personal', {
      body: { email: TEST_EMAIL, password: TEST_PASSWORD, nickname: TEST_NICKNAME },
    })

    const oldToken = getActivationToken(TEST_EMAIL)
    expect(oldToken).toBeTruthy()

    await apiModule.api.post('/identity/auth/activation-email', {
      body: { email: TEST_EMAIL },
    })

    // 重发后应生成新 token（不同于旧 token）
    const newToken = getActivationToken(TEST_EMAIL)
    expect(newToken).toBeTruthy()
    expect(newToken).not.toBe(oldToken)

    // 新 token 应可用
    await apiModule.api.post('/identity/auth/activate', {
      body: { token: newToken! },
    })
  })

  /* ---- 场景 15: 向未注册邮箱重发应失败 ---- */
  it('向未注册邮箱重发激活邮件应返回 10011', async () => {
    const apiModule = await import('@/api')

    let caughtCode = 0
    try {
      await apiModule.api.post('/identity/auth/activation-email', {
        body: { email: 'ghost@example.com' },
      })
    } catch (error) {
      caughtCode = (error as { code: number }).code
    }

    expect(caughtCode).toBe(10011)
  })

  /* ---- 场景 16: 向已激活账号重发应返回 10012 ---- */
  it('向已激活的账号重发激活邮件应返回 10012', async () => {
    const apiModule = await import('@/api')

    await apiModule.api.post('/identity/auth/register/personal', {
      body: { email: TEST_EMAIL, password: TEST_PASSWORD, nickname: TEST_NICKNAME },
    })

    const token = getActivationToken(TEST_EMAIL)
    await apiModule.api.post('/identity/auth/activate', { body: { token: token! } })

    let caughtCode = 0
    try {
      await apiModule.api.post('/identity/auth/activation-email', {
        body: { email: TEST_EMAIL },
      })
    } catch (error) {
      caughtCode = (error as { code: number }).code
    }

    expect(caughtCode).toBe(10012)
  })

  /* ---- 场景 17: 完整激活闭环（模拟 Web 端先消费 → APP 端收到 10012） ---- */
  it('Web 端激活后 APP 端再激活应返回 10012', async () => {
    const apiModule = await import('@/api')

    // 注册
    await apiModule.api.post('/identity/auth/register/personal', {
      body: { email: TEST_EMAIL, password: TEST_PASSWORD, nickname: TEST_NICKNAME },
    })

    const token = getActivationToken(TEST_EMAIL)

    // Web 端消费 token
    await apiModule.api.post('/identity/auth/activate', { body: { token: token! } })

    // APP 端再次激活（模拟从 scheme 携带 token 打开）
    let appCode = 0
    try {
      await apiModule.api.post('/identity/auth/activate', {
        body: { token: token! },
      })
    } catch (error) {
      appCode = (error as { code: number }).code
    }

    expect(appCode).toBe(10012)
  })

  /* ---- 场景 18: 激活后登录成功（含 pending 清理） ---- */
  it('激活后登录应清除 pending 邮箱和注册表单', async () => {
    const apiModule = await import('@/api')

    // 注册
    await apiModule.api.post('/identity/auth/register/personal', {
      body: { email: TEST_EMAIL, password: TEST_PASSWORD, nickname: TEST_NICKNAME },
    })

    // 激活
    const token = getActivationToken(TEST_EMAIL)
    await apiModule.api.post('/identity/auth/activate', { body: { token: token! } })

    // 模拟注册页写入 pending 信息到 store
    const store = useAuthStore()
    store.pendingActivationEmail = TEST_EMAIL
    store.savedRegisterForm = {
      email: TEST_EMAIL,
      password: TEST_PASSWORD,
      nickname: TEST_NICKNAME,
      type: 'personal',
    }

    // 登录
    await store.login(TEST_EMAIL, TEST_PASSWORD)

    expect(store.isLoggedIn).toBe(true)
    expect(store.pendingActivationEmail).toBeNull()
    expect(store.savedRegisterForm).toBeNull()
  })

  /* ---- 场景 19: 服务端注销 ---- */
  it('调用 logout 端点应成功', async () => {
    const apiModule = await import('@/api')

    // 注册 + 激活 + 登录
    await apiModule.api.post('/identity/auth/register/personal', {
      body: { email: TEST_EMAIL, password: TEST_PASSWORD, nickname: TEST_NICKNAME },
    })
    const token = getActivationToken(TEST_EMAIL)
    await apiModule.api.post('/identity/auth/activate', { body: { token: token! } })

    const store = useAuthStore()
    await store.login(TEST_EMAIL, TEST_PASSWORD)

    // 调用 logout API
    await apiModule.api.post('/identity/auth/logout')

    // 清除本地状态
    store.clearTokens()
    expect(store.isLoggedIn).toBe(false)
  })
})
