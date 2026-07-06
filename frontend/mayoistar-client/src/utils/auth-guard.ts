/**
 * 客户端登录访问守卫。
 *
 * 前置条件：应用启动时已初始化认证 Store。
 * 后置条件：访问受保护页面且未登录时拦截导航并跳转登录页。
 * 不变量：公开浏览页、认证流程页不要求登录；未知页面默认按受保护处理。
 */

const LOGIN_PAGE = '/pages/login/index'

const PUBLIC_PAGE_PATHS = new Set([
  'pages/home/index',
  'pages/discover/index',
  'pages/discover/search',
  'pages/discover/map',
  'pages/activity/detail',
  'pages/activity/location-map',
  'pages/activity/summary-detail',
  'pages/activity/review-detail',
  'pages/login/index',
  'pages/register/index',
  'pages/activate/index',
  'pages/forgot-password/index',
  'pages/reset-password/index',
])

const INTERCEPTED_NAVIGATION_METHODS = [
  'navigateTo',
  'redirectTo',
  'reLaunch',
  'switchTab',
] as const

interface UniNavigationOptions {
  url?: string
  [key: string]: unknown
}

interface UniNavigationInterceptor {
  invoke(options: UniNavigationOptions): boolean | void
}

type UniNavigationMethod = (typeof INTERCEPTED_NAVIGATION_METHODS)[number]
type LoginStateGetter = () => boolean

/**
 * 规范化 uni 页面 URL 为 pages/xxx 形式。
 *
 * 前置条件：url 来自 uni 导航 API，可包含开头斜杠和 query/hash。
 * 后置条件：返回去掉 query/hash 与开头斜杠后的页面路径。
 * 不变量：不修改原始 URL。
 *
 * @param url uni 页面 URL
 * @returns 规范化后的页面路径
 */
export function normalizePagePath(url: string): string {
  return url.split(/[?#]/)[0].replace(/^\/+/, '')
}

/**
 * 判断页面是否需要登录。
 *
 * 前置条件：url 为 uni 页面 URL 或 pages/xxx 路径。
 * 后置条件：公开页返回 false，受保护页和未知页返回 true。
 * 不变量：认证流程页永远不要求登录，避免登录跳转循环。
 *
 * @param url 页面 URL
 * @returns 是否需要登录
 */
export function requiresLogin(url: string): boolean {
  const pagePath = normalizePagePath(url)
  return !PUBLIC_PAGE_PATHS.has(pagePath)
}

/**
 * 未登录访问受保护页时跳转登录页。
 *
 * 前置条件：当前调用发生在导航拦截或页面显示检查阶段。
 * 后置条件：提示用户登录，并打开登录页。
 * 不变量：如果目标本身是登录页，不重复跳转。
 *
 * @param targetUrl 原始目标 URL
 */
export function redirectToLogin(targetUrl: string): void {
  if (normalizePagePath(targetUrl) === normalizePagePath(LOGIN_PAGE)) return
  uni.showToast({ title: '请先登录', icon: 'none' })
  uni.navigateTo({ url: LOGIN_PAGE })
}

/**
 * 判断当前访问是否允许继续。
 *
 * 前置条件：isLoggedInGetter 能同步返回当前登录态。
 * 后置条件：未登录访问受保护页返回 false，并触发登录跳转。
 * 不变量：已登录用户访问任何页面不被拦截。
 *
 * @param url 目标页面 URL
 * @param isLoggedInGetter 登录态读取函数
 * @returns 是否允许访问
 */
export function ensureAuthenticatedAccess(
  url: string,
  isLoggedInGetter: LoginStateGetter,
): boolean {
  if (!requiresLogin(url) || isLoggedInGetter()) return true
  redirectToLogin(url)
  return false
}

/**
 * 注册 uni 导航登录守卫。
 *
 * 前置条件：uni.addInterceptor 可用，且应用只需注册一次。
 * 后置条件：navigateTo、redirectTo、reLaunch、switchTab 访问受保护页时会检查登录态。
 * 不变量：拦截器只处理页面 URL，不改变公开页导航行为。
 *
 * @param isLoggedInGetter 登录态读取函数
 */
export function installAuthGuard(isLoggedInGetter: LoginStateGetter): void {
  if (typeof uni.addInterceptor !== 'function') return

  const interceptor: UniNavigationInterceptor = {
    invoke(options) {
      const url = options.url
      if (!url) return true
      return ensureAuthenticatedAccess(url, isLoggedInGetter)
    },
  }

  for (const method of INTERCEPTED_NAVIGATION_METHODS) {
    uni.addInterceptor(method as UniNavigationMethod, interceptor)
  }
}
