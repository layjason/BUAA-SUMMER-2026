/// <reference types="vite/client" />

declare module '*.vue' {
  import { DefineComponent } from 'vue'
  // eslint-disable-next-line @typescript-eslint/no-explicit-any, @typescript-eslint/ban-types
  const component: DefineComponent<{}, {}, any>
  export default component
}

/**
 * Vite 注入的环境变量类型声明
 *
 * 与 .env.example 中的变量保持同步。
 */
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_USE_MOCK?: string
  readonly VITE_REQUEST_TIMEOUT?: string
  readonly VITE_AMAP_WEB_API_KEY?: string
  readonly VITE_AMAP_REST_BASE_URL?: string
  readonly VITE_AMAP_URI_BASE_URL?: string
  readonly VITE_QR_CODE_API_BASE_URL?: string
  readonly VITE_MOCK_IMAGE_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
