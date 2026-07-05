/**
 * API 配置模块
 *
 * 集中管理 API 层的全局配置项，包括 mock 开关、基础 URL、超时等。
 * 实际取值由环境变量驱动（VITE_*），通过 src/config/env.ts 统一读取。
 *
 * 不变量：USE_MOCK 仅在开发阶段使用，生产构建应设为 false
 */
import { API_BASE_URL as ENV_API_BASE_URL, USE_MOCK as ENV_USE_MOCK, REQUEST_TIMEOUT as ENV_REQUEST_TIMEOUT } from '@/config/env'

/**
 * 是否使用内存态 Mock Server
 *
 * 由环境变量 VITE_USE_MOCK 控制（默认 true）。
 * 设置为 false 后所有请求走真实 HTTP。
 */
export const USE_MOCK = ENV_USE_MOCK

/** 真实 API 基础 URL，由环境变量 VITE_API_BASE_URL 控制 */
export const API_BASE_URL = ENV_API_BASE_URL

/** 请求超时时间（毫秒），由环境变量 VITE_REQUEST_TIMEOUT 控制 */
export const REQUEST_TIMEOUT = ENV_REQUEST_TIMEOUT
