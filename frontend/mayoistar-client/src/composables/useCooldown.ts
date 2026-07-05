import { ref, onUnmounted } from 'vue'

/**
 * 倒计时 composable
 *
 * 封装冷却倒计时的定时器逻辑，自动在组件卸载时清理。
 *
 * @param seconds 冷却时长（秒），默认 60
 * @returns { cooldown, startCooldown } cooldown 为剩余秒数的 Ref，startCooldown 启动倒计时
 *
 * useCooldown(60) -> { cooldown: Ref<number>, startCooldown: () => void }
 */
export function useCooldown(seconds: number = 60) {
  const cooldown = ref(0)
  let timer: ReturnType<typeof setInterval> | null = null

  /**
   * 启动冷却倒计时
   *
   * 前置条件：当前不在冷却中（cooldown === 0）
   * 后置条件：cooldown 从 seconds 递减至 0 后自动停止
   */
  function startCooldown(): void {
    cooldown.value = seconds
    if (timer) clearInterval(timer)
    timer = setInterval(() => {
      cooldown.value--
      if (cooldown.value <= 0) {
        if (timer) clearInterval(timer)
        timer = null
      }
    }, 1000)
  }

  /**
   * 停止冷却倒计时
   *
   * 前置条件：冷却中
   * 后置条件：定时器清除，cooldown 归零
   */
  function stopCooldown(): void {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    cooldown.value = 0
  }

  onUnmounted(() => {
    if (timer) clearInterval(timer)
  })

  return { cooldown, startCooldown, stopCooldown }
}
