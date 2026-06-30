/**
 * API HTTP 客户端
 *
 * 基于 uni.request 的类型安全 HTTP 客户端。
 * 所有请求/响应类型均从 OpenAPI schema.d.ts 派生，零手写。
 *
 * 前置条件：调用 API 前需初始化 baseUrl、tokenGetter 和 onTokenExpired
 * 后置条件：返回成功响应 data 字段
 * 不变量：Token 过期时自动刷新一次并重试，刷新失败时调用 onTokenExpired
 */
import type { paths } from './types/schema'
import { BusinessError, TokenExpiredError } from './types'

/* ---- 配置 ---- */

let baseUrl = 'http://localhost:4010'

export function setBaseUrl(url: string): void {
  baseUrl = url.replace(/\/+$/, '')
}

type TokenGetter = () => string | null
let tokenGetter: TokenGetter = () => null

export function setTokenGetter(getter: TokenGetter): void {
  tokenGetter = getter
}

/** 刷新令牌获取器，供 refreshAndRetry 使用 */
type RefreshTokenGetter = () => string | null
let refreshTokenGetter: RefreshTokenGetter = () => null

export function setRefreshTokenGetter(getter: RefreshTokenGetter): void {
  refreshTokenGetter = getter
}

/** Token 过期回调，刷新失败时调用，典型用途：清除认证状态并跳转登录页 */
type TokenExpiredHandler = () => void
let onTokenExpired: TokenExpiredHandler = () => {}

export function setOnTokenExpired(handler: TokenExpiredHandler): void {
  onTokenExpired = handler
}

/** 刷新 Token 并更新 tokenGetter 的回调 */
type TokenSaver = (accessToken: string, refreshToken: string) => void
let tokenSaver: TokenSaver = () => {}

export function setTokenSaver(saver: TokenSaver): void {
  tokenSaver = saver
}

/* ---- 从路径直接提取操作类型 ---- */

/**
 * 提取路径中各 HTTP 方法的操作类型。
 * 所有路径均定义了 get/post/put/patch/delete 字段，未使用的为 never。
 */
type GetOpType<P extends keyof paths> = paths[P]['get']
type PostOpType<P extends keyof paths> = paths[P]['post']
type PutOpType<P extends keyof paths> = paths[P]['put']
type PatchOpType<P extends keyof paths> = paths[P]['patch']
type DeleteOpType<P extends keyof paths> = paths[P]['delete']

/* ---- 从操作类型中提取请求/响应 ---- */

/**
 * 请求体
 */
type OpBody<Op> = Op extends { requestBody: { content: { 'application/json': infer B } } }
  ? B
  : never

/**
 * 查询参数
 */
type OpQuery<Op> = Op extends { parameters: { query: infer Q } } ? { [K in keyof Q]?: Q[K] } : never

/**
 * 路径参数
 */
type OpPath<Op> = Op extends { parameters: { path: infer P } } ? { [K in keyof P]: P[K] } : never

/**
 * 响应 data
 */
type OpData<Op> = Op extends {
  responses: {
    200: {
      content: { 'application/json': infer R }
    }
  }
}
  ? R extends { code: 200; data: infer D; message: 'For Super Earth!' }
    ? D
    : never
  : never

/* ---- 路径参数替换 ---- */

function resolvePath(path: string, params?: Record<string, unknown>): string {
  if (!params) return path
  return path.replace(/\{(\w+)\}/g, (_, key: string) => {
    const value = params[key]
    if (value === undefined) throw new Error(`缺少路径参数: ${key}`)
    return String(value)
  })
}

/* ---- 请求头 ---- */

function buildHeaders(): Record<string, string> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  const token = tokenGetter()
  if (token) headers['Authorization'] = `Bearer ${token}`
  return headers
}

/* ---- Token 刷新拦截器 ---- */

/** 是否正在刷新 Token（防止并发刷新） */
let refreshing = false
/** 刷新期间挂起的请求队列 */
let pendingQueue: Array<{
  resolve: (value: { code: number; message: string; data: unknown }) => void
  reject: (reason: unknown) => void
  retryCtx: object
}> = []

/**
 * 内部请求 — 不走 Token 刷新拦截器，用于刷新 Token 请求自身
 */
function internalRawRequest(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  url: string,
  body?: unknown,
): Promise<{ code: number; message: string; data: unknown }> {
  return new Promise((resolve, reject) => {
    const opts: UniApp.RequestOptions = {
      url: `${baseUrl}${url}`,
      method,
      header: { 'Content-Type': 'application/json', ...buildHeaders() },
      success: (res) => {
        const resBody = res.data as { code: number; message: string; data: unknown }
        if (res.statusCode === 200 && resBody.code === 200) resolve(resBody)
        else
          reject(new BusinessError(resBody.code ?? res.statusCode, resBody.message ?? '请求失败'))
      },
      fail: (err) => reject(new Error(`网络请求失败: ${err.errMsg}`)),
    }
    if (body !== undefined) opts.data = body as Record<string, unknown>
    uni.request(opts)
  })
}

/**
 * 刷新 Token 并重试挂起队列中的所有请求
 *
 * 前置条件：refreshing 为 false，pendingQueue 非空
 * 后置条件：成功则更新 tokenSaver 重试所有请求；失败则拒绝所有请求并调用 onTokenExpired
 */
async function refreshAndRetry(): Promise<void> {
  refreshing = true
  try {
    const storedRefreshToken = refreshTokenGetter()
    if (!storedRefreshToken) throw new TokenExpiredError()

    const result = await internalRawRequest('POST', '/identity/auth/refresh', {
      refreshToken: storedRefreshToken,
    })

    tokenSaver(
      (result.data as { accessToken: string; refreshToken: string }).accessToken,
      (result.data as { accessToken: string; refreshToken: string }).refreshToken,
    )

    // 重试挂起队列中的所有请求
    const queue = pendingQueue
    pendingQueue = []
    for (const { resolve, reject, retryCtx } of queue) {
      try {
        const res = await doRawRequest(retryCtx, false)
        resolve(res)
      } catch (err) {
        reject(err)
      }
    }
  } catch {
    // 刷新失败：通知所有挂起请求
    const queue = pendingQueue
    pendingQueue = []
    const expiredErr = new TokenExpiredError()
    for (const { reject } of queue) {
      reject(expiredErr)
    }
    onTokenExpired()
  } finally {
    refreshing = false
  }
}

// 保存每个挂起请求的上下文以便重试
const retryContextMap = new WeakMap<object, { method: string; url: string; body?: unknown }>()

function setRetryContext(ctx: object, info: { method: string; url: string; body?: unknown }): void {
  retryContextMap.set(ctx, info)
}

function getRetryContext(ctx: object): { method: string; url: string; body?: unknown } {
  return retryContextMap.get(ctx) ?? { method: 'GET', url: '' }
}

/* ---- 核心请求 ---- */

function doRawRequest(
  retryCtx: object,
  allowRefresh: boolean,
): Promise<{ code: number; message: string; data: unknown }> {
  const { method, url, body } = getRetryContext(retryCtx)
  return new Promise((resolve, reject) => {
    const opts: UniApp.RequestOptions = {
      url: `${baseUrl}${url}`,
      method: method as UniApp.RequestOptions['method'],
      header: buildHeaders(),
      success: (res) => {
        const resBody = res.data as { code: number; message: string; data: unknown }
        if (res.statusCode === 200) {
          if (resBody.code === 200) {
            resolve(resBody)
          } else if (resBody.code === 401 && allowRefresh) {
            pendingQueue.push({ resolve, reject, retryCtx })
            setRetryContext(retryCtx, { method, url, body })
            if (!refreshing) refreshAndRetry()
          } else if (resBody.code === 401) {
            reject(new TokenExpiredError())
          } else {
            reject(new BusinessError(resBody.code, resBody.message))
          }
        } else if (res.statusCode === 401 && allowRefresh) {
          pendingQueue.push({ resolve, reject, retryCtx })
          setRetryContext(retryCtx, { method, url, body })
          if (!refreshing) refreshAndRetry()
        } else if (res.statusCode === 401) {
          reject(new TokenExpiredError())
        } else {
          reject(
            new BusinessError(
              res.statusCode,
              (resBody as { message?: string })?.message ?? '请求失败',
            ),
          )
        }
      },
      fail: (err) => reject(new Error(`网络请求失败: ${err.errMsg}`)),
    }
    if (body !== undefined) opts.data = body as Record<string, unknown>
    uni.request(opts)
  })
}

function rawRequest(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  url: string,
  body?: unknown,
): Promise<{ code: number; message: string; data: unknown }> {
  const ctx = {}
  setRetryContext(ctx, { method, url, body })
  return doRawRequest(ctx, true)
}

async function extractData<T>(
  promise: Promise<{ code: number; message: string; data: unknown }>,
): Promise<T> {
  return (await promise).data as T
}

/* ---- 公开 API ---- */

/**
 * GET 请求
 */
export function get<P extends keyof paths>(
  path: P,
  options?: { path?: OpPath<GetOpType<P>>; query?: OpQuery<GetOpType<P>> },
): Promise<OpData<GetOpType<P>>> {
  let fullPath = resolvePath(path as string, options?.path)
  if (options?.query) {
    const params = new URLSearchParams()
    for (const [k, v] of Object.entries(options.query)) {
      if (v !== undefined && v !== null) params.append(k, String(v))
    }
    const qs = params.toString()
    if (qs) fullPath += `?${qs}`
  }
  return extractData(rawRequest('GET', fullPath))
}

/**
 * POST 请求
 */
export function post<P extends keyof paths>(
  path: P,
  options?: { path?: OpPath<PostOpType<P>>; body?: OpBody<PostOpType<P>> },
): Promise<OpData<PostOpType<P>>> {
  return extractData(rawRequest('POST', resolvePath(path as string, options?.path), options?.body))
}

/**
 * PUT 请求
 */
export function put<P extends keyof paths>(
  path: P,
  options?: { path?: OpPath<PutOpType<P>>; body?: OpBody<PutOpType<P>> },
): Promise<OpData<PutOpType<P>>> {
  return extractData(rawRequest('PUT', resolvePath(path as string, options?.path), options?.body))
}

/**
 * PATCH 请求（uni-app 3.0 不完全支持 PATCH，使用 PUT 替代发送）
 */
export function patch<P extends keyof paths>(
  path: P,
  options?: { path?: OpPath<PatchOpType<P>>; body?: OpBody<PatchOpType<P>> },
): Promise<OpData<PatchOpType<P>>> {
  return extractData(rawRequest('PUT', resolvePath(path as string, options?.path), options?.body))
}

/**
 * DELETE 请求
 */
export function del<P extends keyof paths>(
  path: P,
  options?: { path?: OpPath<DeleteOpType<P>> },
): Promise<OpData<DeleteOpType<P>>> {
  return extractData(rawRequest('DELETE', resolvePath(path as string, options?.path)))
}

/**
 * 文件上传
 */
export function upload<T>(
  uploadPath: string,
  filePath: string,
  formData?: Record<string, string>,
): Promise<T> {
  const url = `${baseUrl}${uploadPath}`
  const authHeader: Record<string, string> = {}
  const token = tokenGetter()
  if (token) authHeader['Authorization'] = `Bearer ${token}`

  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url,
      filePath,
      name: 'file',
      formData,
      header: authHeader,
      success: (res) => {
        if (res.statusCode === 200) {
          try {
            const body = JSON.parse(res.data) as { code: number; message: string; data: T }
            if (body.code === 200) resolve(body.data)
            else reject(new BusinessError(body.code, body.message))
          } catch {
            reject(new Error('上传响应解析失败'))
          }
        } else {
          reject(new Error(`上传失败: ${res.statusCode}`))
        }
      },
      fail: (err) => reject(new Error(`上传失败: ${err.errMsg}`)),
    })
  })
}
