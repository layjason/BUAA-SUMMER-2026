/**
 * 从 API 分页响应中提取列表项
 *
 * @param result API 返回的 data（数组或 PageResult）
 */
export function extractPageItems<T>(result: unknown): T[] {
  if (Array.isArray(result)) return result as T[]
  return ((result as Record<string, unknown>).items as T[]) ?? []
}
