import { describe, expect, it } from 'vitest'
import {
  isBackendMediaSignedUrl,
  requiresAuthenticatedMediaDownload,
  toAbsoluteMediaUrl,
} from '@/utils/media-preview'

describe('媒体预览 URL 工具', () => {
  it('应识别后端媒体签名地址并转换相对 URL', () => {
    const signedUrl = '/media/11111111-1111-1111-1111-111111111111?v=1&policy=publicAccess'

    expect(isBackendMediaSignedUrl(signedUrl)).toBe(true)
    expect(toAbsoluteMediaUrl(signedUrl)).toBe(
      'http://localhost:8080/media/11111111-1111-1111-1111-111111111111?v=1&policy=publicAccess',
    )
  })

  it('非 publicAccess 的后端媒体应要求鉴权下载', () => {
    expect(requiresAuthenticatedMediaDownload('/media/id?v=1&policy=activityOwner')).toBe(true)
    expect(requiresAuthenticatedMediaDownload('/media/id?v=1&policy=publicAccess')).toBe(false)
    expect(requiresAuthenticatedMediaDownload('https://example.com/image.jpg')).toBe(false)
  })
})
