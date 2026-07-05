/**
 * API 请求层
 *
 * 页面和组件通过 src/api/modules/* 调用 API，
 * modules 只能调用本文件。本文件根据 config.USE_MOCK
 * 决定走 mockServer 还是真实 HTTP（client.ts）。
 *
 * 页面不能感知 mock/real API 差异。
 *
 * 前置条件：main.ts 已初始化 mock handler（当 USE_MOCK 为 true 时）
 * 后置条件：返回从 schema.d.ts 派生的强类型响应数据
 * 不变量：client.ts 的 mockHandler 机制保证 mock/real 透明切换
 */

// 兼容层：直接重新导出 client.ts 的全部公开 API
// 当 USE_MOCK = true 时，main.ts 已将 mockServer.handleMockRequest 注入 client.ts 的 mockHandler
// 当 USE_MOCK = false 时，mockHandler 为 null，所有请求走真实 HTTP
export { get, post, put, patch, del, upload, getBinary } from './client'

// 兼容导出错误类型
export { BusinessError, TokenExpiredError } from './types'

// 兼容导出分页类型
export type { PageResult } from './types'
