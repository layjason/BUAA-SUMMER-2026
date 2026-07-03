/**
 * 本地存储工具
 *
 * 封装 uni.getStorageSync / uni.setStorageSync / uni.removeStorageSync
 * 提供类型安全的 KV 存取
 */

/**
 * 从本地存储读取值
 *
 * 前置条件：key 存在且值可被 JSON 解析
 * 后置条件：返回对应的类型值
 *
 * @param key 存储键
 * @returns 存储的值，若不存在则返回 null
 */
export function getStorageItem<T>(key: string): T | null {
  try {
    const raw = uni.getStorageSync(key)
    if (raw === '' || raw === undefined || raw === null) return null
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

/**
 * 写入本地存储
 *
 * 前置条件：value 可被 JSON 序列化
 * 后置条件：key 对应的值被持久化
 *
 * @param key 存储键
 * @param value 值
 */
export function setStorageItem<T>(key: string, value: T): void {
  try {
    uni.setStorageSync(key, JSON.stringify(value))
  } catch {
    /* 存储不可用 */
  }
}

/**
 * 删除本地存储项
 *
 * @param key 存储键
 */
export function removeStorageItem(key: string): void {
  try {
    uni.removeStorageSync(key)
  } catch {
    /* 存储不可用 */
  }
}
