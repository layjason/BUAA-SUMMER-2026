import type { components } from '@/api/types/schema'
import { resolveMediaPreviewUrl, toAbsoluteMediaUrl } from '@/utils/media-preview'

type MediaFile = components['schemas']['MediaFile']

/** 从 OpenAPI MediaFile 提取头像 signedUrl */
export function extractAvatarSignedUrl(media?: MediaFile | null, fallbackUrl = ''): string {
  return media?.signedUrl?.trim() || fallbackUrl.trim()
}

/** 将头像 signedUrl 转为 image 可展示的绝对地址（公开头像同步即可） */
export function toAvatarAbsoluteUrl(signedUrl: string | null | undefined): string {
  const normalized = signedUrl?.trim() ?? ''
  if (!normalized) return ''
  return toAbsoluteMediaUrl(normalized)
}

/**
 * 解析头像为 image 组件可展示地址。
 *
 * 公开头像返回绝对 URL；需鉴权的后端媒体在有 token 时下载为本地临时文件。
 */
export async function resolveAvatarDisplayUrl(
  signedUrl: string | null | undefined,
  accessToken: string | null,
): Promise<string> {
  const normalized = signedUrl?.trim() ?? ''
  if (!normalized) return ''
  return resolveMediaPreviewUrl(normalized, accessToken)
}
