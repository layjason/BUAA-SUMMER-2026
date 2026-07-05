/**
 * API 模块统一导出
 *
 * 按领域分组导出所有 API 模块，页面通过 api.auth.xxx、api.activities.xxx 等方式调用。
 */
export * as auth from './auth'
export * as profile from './profile'
export * as activities from './activities'
export * as registrations from './registrations'
export * as checkin from './checkin'
export * as ai from './ai'
export * as social from './social'
export * as teams from './teams'
export * as chat from './chat'
