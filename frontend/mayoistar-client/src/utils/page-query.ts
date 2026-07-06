/**
 * 页面 query 参数辅助
 */

type QueryValue = string | number | boolean | null | undefined

/**
 * 从页面 query 中读取第一个可用字符串参数。
 *
 * 前置条件：query 来自 uni-app 页面生命周期，值可能为字符串、数字或空值。
 * 后置条件：按 names 顺序返回第一个非空参数的字符串值；不存在时返回空字符串。
 * 不变量：不修改 query 原对象。
 *
 * @param query 页面 query 对象
 * @param names 允许的参数名，越靠前优先级越高
 */
export function readFirstQueryString(
  query: Record<string, QueryValue> | undefined,
  names: string[],
): string {
  if (!query) return ''
  for (const name of names) {
    const value = query[name]
    if (value == null) continue
    const text = String(value).trim()
    if (text) return decodeURIComponent(text)
  }
  return ''
}
