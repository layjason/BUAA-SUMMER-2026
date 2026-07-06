import { describe, expect, it, vi } from 'vitest'
import { mockMediaFile } from '@/test-utils/mock-media-file'
import {
  collectUserIdsMissingAvatar,
  getCachedAvatar,
  getCachedNickname,
  loadUserProfileCache,
  resolveCachedAvatar,
} from '@/utils/profile-cache'

const { getUserProfileMock } = vi.hoisted(() => ({
  getUserProfileMock: vi.fn(),
}))

vi.mock('@/api/modules/social', () => ({
  getUserProfile: getUserProfileMock,
}))

describe('profile-cache', () => {
  it('loads nickname and avatar into cache', async () => {
    getUserProfileMock.mockReset()
    getUserProfileMock.mockResolvedValueOnce({
      nickname: '小明',
      avatar: mockMediaFile({
        mediaId: 'a1',
        signedUrl: '/media/avatar-10001.jpg',
        usage: 'avatar',
      }),
    })

    const cache = await loadUserProfileCache(['10001'])

    expect(getUserProfileMock).toHaveBeenCalledWith('10001')
    expect(cache['10001'].nickname).toBe('小明')
    expect(cache['10001'].avatar?.signedUrl).toBe('/media/avatar-10001.jpg')
  })

  it('collectUserIdsMissingAvatar skips users that already have avatar', () => {
    const ids = collectUserIdsMissingAvatar([
      {
        userId: '10001',
        avatar: mockMediaFile({
          mediaId: 'a1',
          signedUrl: '/media/avatar-10001.jpg',
          usage: 'avatar',
        }),
      },
      { userId: '10002' },
    ])

    expect(ids).toEqual(['10002'])
  })

  it('resolveCachedAvatar prefers cache and falls back to list avatar', () => {
    const cache = {
      '10001': {
        nickname: '小明',
        avatar: mockMediaFile({
          mediaId: 'c1',
          signedUrl: '/media/cached.jpg',
          usage: 'avatar',
        }),
      },
    }

    expect(resolveCachedAvatar(cache, '10001')).toEqual(cache['10001'].avatar)
    expect(
      resolveCachedAvatar(
        cache,
        '10002',
        mockMediaFile({
          mediaId: 'f1',
          signedUrl: '/media/fallback.jpg',
          usage: 'avatar',
        }),
      )?.signedUrl,
    ).toBe('/media/fallback.jpg')
    expect(getCachedNickname(cache, '10001')).toBe('小明')
    expect(getCachedAvatar(cache, '10002')).toBeUndefined()
  })
})
