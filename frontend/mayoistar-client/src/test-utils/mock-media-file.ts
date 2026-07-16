import type { MediaFile } from '@/api/types/activity-schemas'

/** 构造符合 OpenAPI MediaFile 的测试数据 */
export function mockMediaFile(
  overrides: Partial<MediaFile> & Pick<MediaFile, 'mediaId'>,
): MediaFile {
  return {
    contentType: 'image/jpeg',
    fileName: 'test.jpg',
    sizeBytes: 1024,
    uploadedAt: '2026-07-01T00:00:00+08:00',
    usage: 'activityImage',
    signedUrl: '/media/test.jpg',
    ...overrides,
  }
}
