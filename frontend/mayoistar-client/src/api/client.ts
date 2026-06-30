/**
 * API HTTP 客户端
 *
 * 基于 uni.request 的类型安全 HTTP 客户端。
 * 所有请求/响应类型均从 OpenAPI schema.d.ts 派生，零手写。
 *
 * 前置条件：调用 API 前需初始化 baseUrl 和 tokenGetter
 * 后置条件：返回成功响应 data 字段
 * 不变量：JSON 响应统一经过 APIResult 包装解析
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

/* ---- 核心请求 ---- */

function rawRequest(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  url: string,
  body?: unknown,
): Promise<{ code: number; message: string; data: unknown }> {
  return new Promise((resolve, reject) => {
    const opts: UniApp.RequestOptions = {
      url: `${baseUrl}${url}`,
      method,
      header: buildHeaders(),
      success: (res) => {
        const resBody = res.data as { code: number; message: string; data: unknown }
        if (res.statusCode === 200) {
          if (resBody.code === 200) resolve(resBody)
          else if (resBody.code === 401) reject(new TokenExpiredError())
          else reject(new BusinessError(resBody.code, resBody.message))
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
