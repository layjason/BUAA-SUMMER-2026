import { beforeEach, describe, expect, it, vi } from 'vitest'

const postMock = vi.fn()

vi.mock('@/api/request', () => ({
  post: (...args: unknown[]) => postMock(...args),
}))

const { changePassword } = await import('@/api/modules/auth')

describe('auth API 模块契约', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('修改密码必须调用当前用户密码端点并传递新旧密码', () => {
    changePassword('OldPass1234', 'NewPass1234')

    expect(postMock).toHaveBeenCalledWith('/identity/me/password', {
      body: {
        oldPassword: 'OldPass1234',
        newPassword: 'NewPass1234',
      },
    })
  })
})
