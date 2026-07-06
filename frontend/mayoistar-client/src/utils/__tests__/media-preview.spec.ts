import { describe, expect, it } from 'vitest'
import {
  isBackendMediaSignedUrl,
  requiresAuthenticatedMediaDownload,
  resolveMediaPreviewUrlMap,
  toAbsoluteMediaUrl,
} from '@/utils/media-preview'

describe('媒体预览 URL 工具', () => {
  it('应识别后端媒体签名地址并转换相对 URL', () => {
    const signedUrl = '/media/11111111-1111-1111-1111-111111111111?v=1&policy=publicAccess'

    expect(isBackendMediaSignedUrl(signedUrl)).toBe(true)
    expect(toAbsoluteMediaUrl(signedUrl)).toContain('/media/11111111-1111-1111-1111-111111111111')
  })

  it('应识别经浏览器解析后的同源绝对媒体地址', () => {
    expect(
      isBackendMediaSignedUrl(
        'http://localhost:3000/media/' +
          '86af0132-5eb1-414c-abad-93b204c339af?v=2&policy=conversationMember',
      ),
    ).toBe(true)
    expect(
      requiresAuthenticatedMediaDownload(
        'http://localhost:3000/media/' +
          '86af0132-5eb1-414c-abad-93b204c339af?v=2&policy=conversationMember',
      ),
    ).toBe(true)
  })

  it('非 publicAccess 的后端媒体应要求鉴权下载', () => {
    expect(requiresAuthenticatedMediaDownload('/media/id?v=1&policy=activityOwner')).toBe(true)
    expect(requiresAuthenticatedMediaDownload('/media/id?v=1&policy=publicAccess')).toBe(false)
    expect(requiresAuthenticatedMediaDownload('https://example.com/image.jpg')).toBe(false)
  })

  it('批量解析应跳过空 URL 并保留公开地址', async () => {
    const map = await resolveMediaPreviewUrlMap(
      [
        { id: 'a', signedUrl: '' },
        { id: 'b', signedUrl: 'https://example.com/public.jpg' },
      ],
      null,
    )

    expect(map).toEqual({ b: 'https://example.com/public.jpg' })
  })
})
