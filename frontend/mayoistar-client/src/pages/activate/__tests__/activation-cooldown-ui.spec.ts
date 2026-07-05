import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

function readSource(path: string): string {
  return readFileSync(resolve(process.cwd(), path), 'utf8')
}

describe('激活页重发邮件倒计时', () => {
  it('应在页面侧完成 i18n 秒数插值，避免按钮显示缺少数字', () => {
    const activateSource = readSource('src/pages/activate/index.vue')
    const forgotPasswordSource = readSource('src/pages/forgot-password/index.vue')
    const cooldownButtonSource = readSource('src/components/CooldownButton.vue')

    expect(activateSource).toContain("t('activate.resendCooldown', { seconds: cooldown })")
    expect(forgotPasswordSource).toContain(
      "t('forgotPassword.resendCooldown', { seconds: cooldown })",
    )
    expect(cooldownButtonSource).toContain('{{ cooldownText }}')
    expect(cooldownButtonSource).not.toContain("replace('{seconds}'")
  })

  it('普通注册进入激活页后应立即启动重发冷却', () => {
    const source = readSource('src/pages/activate/index.vue')
    const branchStart = source.indexOf('} else if (pendingEmail.value) {')
    const branchEnd = source.indexOf("state.value = 'idle'", branchStart)
    const sentBranch = source.slice(branchStart, branchEnd)

    expect(sentBranch).toContain('authStore.autoResendActivation')
    expect(sentBranch).toContain('await handleResend()')
    expect(sentBranch).toContain('startCooldown()')
  })
})
