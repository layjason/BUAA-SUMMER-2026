/**
 * 开发环境调试开关配置
 *
 * 修改 enabled 为 true 以启用所有调试行为；
 * 单独控制每个功能需同时确保 enabled 为 true。
 *
 * 配置项说明：
 * - enabled:      一键总开关，false 时所有调试行为均不生效
 * - mockApi:      数据模式：拦截 HTTP 请求返回预定义数据，并自动注入登录态
 * - mockUserKind: 数据模式下自动登录使用的模拟用户类型
 * - mockData:     数据模式使用的响应数据，从 mock-data.ts 导入
 *
 * 前置条件：仅开发环境使用，生产构建中应保持 enabled = false
 * 后置条件：enabled 控制所有调试功能的准入
 */

import { mockData as defaultMockData } from './mock-data'

export type MockResponse = {
  code: number
  message: string
  data: unknown
}

export const devConfig = {
  /** 一键总开关 */
  enabled: true,

  /** 数据模式：拦截 HTTP 请求返回预定义 mock 数据，并自动注入登录态 */
  mockApi: true,

  /** 数据模式下自动登录使用的模拟用户类型 */
  mockUserKind: 'personal' as 'personal' | 'merchant',

  /**
   * Mock 响应数据
   *
   * 数据源来自 src/config/mock-data.ts，在此可覆盖或追加。
   * key 格式："METHOD /path"
   */
  mockData: defaultMockData,
}

/**
 * 检查指定调试功能是否启用
 *
 * 同时检查总开关和功能开关，总开关关闭时所有功能返回 false。
 *
 * @param key 功能键名
 * @returns true 表示该调试功能已启用
 */
export function isDevEnabled(key: keyof typeof devConfig): boolean {
  return devConfig.enabled && !!devConfig[key]
}
