/**
 * 应用级状态管理
 *
 * 管理全局 UI 状态：加载中、网络状态、全局错误
 *
 * 前置条件：无
 * 后置条件：所有组件可通过此 store 访问全局 UI 状态
 * 不变量：loadingCounter >= 0
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAppStore = defineStore('app', () => {
  /* ---- 状态 ---- */

  /** 加载计数器，支持嵌套调用 */
  const loadingCounter = ref(0)

  /** 网络状态 */
  const networkStatus = ref<'online' | 'offline'>('online')

  /** 全局错误消息 */
  const globalError = ref<string | null>(null)

  /* ---- 计算属性 ---- */

  /** 是否正在加载 */
  const isLoading = computed(() => loadingCounter.value > 0)

  /* ---- 操作 ---- */

  /**
   * 开始加载
   *
   * 后置条件：loadingCounter 增加 1
   */
  function startLoading(): void {
    loadingCounter.value++
  }

  /**
   * 停止加载
   *
   * 前置条件：loadingCounter > 0
   * 后置条件：loadingCounter 减少 1
   */
  function stopLoading(): void {
    if (loadingCounter.value > 0) {
      loadingCounter.value--
    }
  }

  /**
   * 设置网络状态
   */
  function setNetworkStatus(status: 'online' | 'offline'): void {
    networkStatus.value = status
  }

  /**
   * 显示全局错误
   *
   * 后置条件：globalError 已在 UI 中展示
   */
  function showError(message: string): void {
    globalError.value = message
    uni.showToast({
      title: message,
      icon: 'none',
      duration: 3000,
    })
  }

  /**
   * 清除全局错误
   */
  function clearError(): void {
    globalError.value = null
  }

  return {
    /* 状态 */
    loadingCounter,
    networkStatus,
    globalError,
    /* 计算属性 */
    isLoading,
    /* 操作 */
    startLoading,
    stopLoading,
    setNetworkStatus,
    showError,
    clearError,
  }
})
