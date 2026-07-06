import { API_BASE_URL } from '@/config/env'

/**
 * 判断媒体签名 URL 是否属于后端媒体访问端点。
 *
 * 前置条件：signedUrl 可能为相对路径或绝对 URL。
 * 后置条件：返回 true 表示该 URL 指向后端 /media 端点。
 * 不变量：仅解析字符串，不发起网络请求。
 *
 * @param signedUrl 媒体签名访问地址
 */
export function isBackendMediaSignedUrl(signedUrl: string): boolean {
  if (signedUrl.startsWith('/media/')) return true
  const normalizedApiBase = API_BASE_URL.replace(/\/+$/, '')
  if (normalizedApiBase && signedUrl.startsWith(`${normalizedApiBase}/media/`)) {
    return true
  }
  // H5 开发时 image 组件会把 /media/... 解析为 http://localhost:3000/media/...
  try {
    const pathname = new URL(signedUrl).pathname
    return pathname.startsWith('/media/')
  } catch {
    return false
  }
}

/**
 * 将后端相对媒体 URL 转换为绝对 URL。
 *
 * 前置条件：signedUrl 来自后端 MediaFile.signedUrl。
 * 后置条件：返回可被 App/H5 image 或 downloadFile 访问的绝对 URL。
 * 不变量：不会修改签名查询参数。
 *
 * @param signedUrl 媒体签名访问地址
 */
export function toAbsoluteMediaUrl(signedUrl: string): string {
  if (/^https?:\/\//i.test(signedUrl)) return signedUrl
  const base = API_BASE_URL.replace(/\/+$/, '')
  const path = signedUrl.replace(/^\/+/, '')
  // 开发环境 API_BASE_URL 为空时保持相对路径，走 Vite 同源代理
  if (!base) return `/${path}`
  return `${base}/${path}`
}

/**
 * 判断签名媒体是否需要鉴权下载。
 *
 * 前置条件：signedUrl 指向后端 /media 端点，可能包含 policy 查询参数。
 * 后置条件：publicAccess 之外的后端媒体返回 true。
 * 不变量：只根据 URL 查询参数判断，不校验后端权限。
 *
 * @param signedUrl 媒体签名访问地址
 */
export function requiresAuthenticatedMediaDownload(signedUrl: string): boolean {
  if (!isBackendMediaSignedUrl(signedUrl)) return false
  const query = signedUrl.split('?')[1] ?? ''
  const params = new URLSearchParams(query)
  return params.get('policy') !== 'publicAccess'
}

/**
 * 将媒体签名 URL 解析为 image 组件可展示的地址。
 *
 * 前置条件：signedUrl 可能为空、后端相对 URL、后端绝对 URL 或外部公开 URL；accessToken 可能为空。
 * 后置条件：公开媒体返回绝对 URL；私有后端媒体在有 token 时下载为本地临时文件；失败时返回空字符串。
 * 不变量：不改变媒体权限策略，不持久化下载结果。
 *
 * @param signedUrl 媒体签名访问地址
 * @param accessToken 当前访问令牌，可为空
 */
export async function resolveMediaPreviewUrl(
  signedUrl: string | null | undefined,
  accessToken: string | null,
): Promise<string> {
  const normalizedUrl = signedUrl ?? ''
  if (!normalizedUrl) return ''
  if (!isBackendMediaSignedUrl(normalizedUrl)) return normalizedUrl

  const absoluteUrl = toAbsoluteMediaUrl(normalizedUrl)
  if (!requiresAuthenticatedMediaDownload(normalizedUrl)) return absoluteUrl
  if (!accessToken) return ''

  try {
    const result = await uni.downloadFile({
      url: absoluteUrl,
      header: { Authorization: `Bearer ${accessToken}` },
    })
    if (!result || result.statusCode !== 200 || !result.tempFilePath) return ''
    return result.tempFilePath
  } catch {
    return ''
  }
}

/**
 * 批量解析媒体列表的可展示地址。
 *
 * 前置条件：items 中 id 唯一，signedUrl 可能为空。
 * 后置条件：返回仅包含解析成功项的 id -> previewUrl 映射。
 * 不变量：不改变媒体权限策略，不持久化下载结果。
 *
 * @param items 待解析的媒体项
 * @param accessToken 当前访问令牌，可为空
 */
export async function resolveMediaPreviewUrlMap(
  items: ReadonlyArray<{ id: string; signedUrl?: string | null }>,
  accessToken: string | null,
): Promise<Record<string, string>> {
  const results = await Promise.all(
    items.map(async (item) => {
      const preview = await resolveMediaPreviewUrl(item.signedUrl, accessToken)
      return { id: item.id, preview }
    }),
  )
  const map: Record<string, string> = {}
  for (const { id, preview } of results) {
    if (preview) map[id] = preview
  }
  return map
}
