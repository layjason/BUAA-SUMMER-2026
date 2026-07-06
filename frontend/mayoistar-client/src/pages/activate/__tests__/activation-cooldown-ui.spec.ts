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

    expect(activateSource).toContain("formatI18nTemplate(String(tm('activate.resendCooldown')),")
    expect(forgotPasswordSource).toContain(
      "formatI18nTemplate(String(tm('forgotPassword.resendCooldown')),",
    )
    expect(cooldownButtonSource).toContain('{{ cooldownText }}')
    expect(cooldownButtonSource).not.toContain("t('activate.resendCooldown'")
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

  it('待激活状态应提供查询按钮，成功状态不展示重发和重新注册操作', () => {
    const source = readSource('src/pages/activate/index.vue')
    const actionStart = source.indexOf(
      "v-if=\"pendingEmail && (state === 'sent' || state === 'error')\"",
    )
    const actionEnd = source.indexOf('</view>', source.indexOf("t('activate.emailWrong')"))
    const pendingActions = source.slice(actionStart, actionEnd)

    expect(pendingActions).toContain("t('activate.checkStatusButton')")
    expect(pendingActions).toContain('handleCheckActivationStatus')
    expect(pendingActions).toContain("t('activate.resendButton')")
    expect(pendingActions).toContain("t('activate.emailWrong')")
    expect(source).toContain("state === 'success' && shouldShowLoginButton")
    expect(source).toContain('const shouldShowLoginButton = computed(')
  })
})
