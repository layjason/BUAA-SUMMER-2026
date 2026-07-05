/**
 * 环境变量集中管理模块
 *
 * 所有运行时配置统一从 import.meta.env 读取，提供类型安全的默认值。
 * 其他模块应从此处导入配置，而非直接访问 import.meta.env。
 *
 * 前置条件：Vite 已加载 .env 文件并注入 VITE_* 变量
 * 后置条件：导出的 env 对象包含所有运行时配置项
 * 不变量：所有值均有合理默认值，读取失败时使用默认值而非抛出异常
 */

/** 后端 API 基础地址 */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/** 是否启用 Mock Server */
export const USE_MOCK: boolean = import.meta.env.VITE_USE_MOCK !== 'false'

/** HTTP 请求超时时间（毫秒） */
export const REQUEST_TIMEOUT: number = Number(import.meta.env.VITE_REQUEST_TIMEOUT) || 15000

/** 高德 Web API Key */
export const AMAP_WEB_API_KEY: string = import.meta.env.VITE_AMAP_WEB_API_KEY || ''

/** 高德 REST API 基础地址 */
export const AMAP_REST_BASE_URL: string =
  import.meta.env.VITE_AMAP_REST_BASE_URL || 'https://restapi.amap.com/v3'

/** 高德 URI 短链基础地址 */
export const AMAP_URI_BASE_URL: string =
  import.meta.env.VITE_AMAP_URI_BASE_URL || 'https://uri.amap.com'

/** 第三方二维码生成 API 基础 URL */
export const QR_CODE_API_BASE_URL: string =
  import.meta.env.VITE_QR_CODE_API_BASE_URL || 'https://api.qrserver.com/v1/create-qr-code/'

/** Mock 数据中使用的占位图片服务基础 URL */
export const MOCK_IMAGE_BASE_URL: string =
  import.meta.env.VITE_MOCK_IMAGE_BASE_URL || 'https://picsum.photos'
