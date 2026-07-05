/**
 * API 配置模块
 *
 * 集中管理 API 层的全局配置项，包括 mock 开关、基础 URL、超时等。
 * 修改 USE_MOCK 即可切换 mock/真实 API 模式。
 *
 * 不变量：USE_MOCK 仅在开发阶段使用，生产构建应设为 false
 */

/**
 * 是否使用内存态 Mock Server
 *
 * true  → 所有请求走 src/mock/mockServer.ts（有状态 workflow）
 * false → 所有请求走真实 HTTP（uni.request → 后端）
 */
export const USE_MOCK = true

/** 真实 API 基础 URL */
export const API_BASE_URL = 'http://localhost:4010'

/** 请求超时时间（毫秒） */
export const REQUEST_TIMEOUT = 15000
