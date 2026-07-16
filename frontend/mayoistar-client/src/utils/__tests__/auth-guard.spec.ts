import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ensureAuthenticatedAccess,
  installAuthGuard,
  normalizePagePath,
  requiresLogin,
} from '@/utils/auth-guard'

interface CapturedInterceptor {
  invoke(options: { url?: string }): boolean | void
}

describe('auth-guard', () => {
  const showToast = vi.fn()
  const navigateTo = vi.fn()

  beforeEach(() => {
    showToast.mockReset()
    navigateTo.mockReset()
    vi.stubGlobal('uni', {
      showToast,
      navigateTo,
    })
  })

  it('应规范化页面路径并移除 query 与 hash', () => {
    expect(normalizePagePath('/pages/activity/detail?activityId=1#top')).toBe(
      'pages/activity/detail',
    )
  })

  it('公开浏览和认证流程页面不要求登录', () => {
    expect(requiresLogin('/pages/home/index')).toBe(false)
    expect(requiresLogin('/pages/discover/search?keyword=羽毛球')).toBe(false)
    expect(requiresLogin('/pages/activity/detail?activityId=1')).toBe(false)
    expect(requiresLogin('/pages/login/index')).toBe(false)
  })

  it('发布、消息、社交、队伍和活动操作页面要求登录', () => {
    expect(requiresLogin('/pages/publish/index')).toBe(true)
    expect(requiresLogin('/pages/messages/index')).toBe(true)
    expect(requiresLogin('/pages/social/friends')).toBe(true)
    expect(requiresLogin('/pages/teams/index')).toBe(true)
    expect(requiresLogin('/pages/activity/edit')).toBe(true)
  })

  it('未登录访问受保护页面时应提示并跳转登录页', () => {
    const allowed = ensureAuthenticatedAccess('/pages/activity/edit', () => false)

    expect(allowed).toBe(false)
    expect(showToast).toHaveBeenCalledWith({ title: '请先登录', icon: 'none' })
    expect(navigateTo).toHaveBeenCalledWith({ url: '/pages/login/index' })
  })

  it('已登录访问受保护页面时应直接放行', () => {
    const allowed = ensureAuthenticatedAccess('/pages/activity/edit', () => true)

    expect(allowed).toBe(true)
    expect(navigateTo).not.toHaveBeenCalled()
  })

  it('注册导航拦截器后应拦截未登录的受保护页面跳转', () => {
    const interceptors = new Map<string, CapturedInterceptor>()
    const addInterceptor = vi.fn((method: string, interceptor: CapturedInterceptor) => {
      interceptors.set(method, interceptor)
    })
    vi.stubGlobal('uni', {
      showToast,
      navigateTo,
      addInterceptor,
    })

    installAuthGuard(() => false)

    expect(addInterceptor).toHaveBeenCalledTimes(4)
    expect(interceptors.get('switchTab')?.invoke({ url: '/pages/messages/index' })).toBe(false)
    expect(interceptors.get('navigateTo')?.invoke({ url: '/pages/discover/search' })).toBe(true)
  })
})
