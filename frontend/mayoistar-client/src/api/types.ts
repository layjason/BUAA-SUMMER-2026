/**
 * API 类型工具层
 *
 * 从 openapi-typescript 生成的结构中提取请求/响应类型。
 * 本文件中的类型全部从 schema.d.ts 派生，零手写。
 */

/**
 * 平台统一错误响应
 */
export interface ApiErrorResponse {
  code: number
  message: string
}

/**
 * 业务异常，包含错误码和英文模板消息
 */
export class BusinessError extends Error {
  /** 业务错误码 */
  code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'BusinessError'
    this.code = code
  }
}

/**
 * Token 过期异常
 */
export class TokenExpiredError extends Error {
  constructor() {
    super('Authentication token has expired')
    this.name = 'TokenExpiredError'
  }
}

/**
 * 分页响应
 *
 * @template T 列表项类型
 */
export type PageResult<T> = {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}
