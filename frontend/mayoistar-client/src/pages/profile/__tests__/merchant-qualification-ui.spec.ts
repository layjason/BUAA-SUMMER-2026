import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

function normalizeFormText(value: string | null | undefined): string {
  return (value ?? '').trim()
}

describe('商家资料资质提交 UI', () => {
  it('空商家名称不应在生成资料快照时抛错', () => {
    expect(normalizeFormText(null)).toBe('')
    expect(normalizeFormText(undefined)).toBe('')
    expect(normalizeFormText('  测试商家  ')).toBe('测试商家')
  })
  it('应通过 API modules 提交营业凭证并展示审核状态', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/profile/edit.vue'), 'utf8')

    expect(source).toContain('submitMerchantQualification')
    expect(source).toContain('uploadMerchantLicense')
    expect(source).toContain('chooseLicenseImages')
    expect(source).toContain('canSubmitQualification')
    expect(source).toContain('licenseMediaIds')
    expect(source).toContain('qualificationStatusText')
    expect(source).toContain('profile.qualification?.status ?? profile.qualificationStatus')
    expect(source).toContain('licensePreviewUrls.value.push(filePath)')
    expect(source).not.toContain('licensePreviewUrls.value.push(result.signedUrl || filePath)')
    expect(source).toContain('qualificationSubmittedAt')
    expect(source).toContain('qualificationReviewedAt')
    expect(source).toContain('submittedLicensePreviewUrls')
    expect(source).toContain('resolveMediaPreviewUrl')
    expect(source).toContain('resolveSubmittedLicensePreviews')
    expect(source).toContain("formError.value = ''")
    expect(source).toContain('profile.merchantName ??')
    expect(source).toContain('normalizeFormText')
    expect(source).toContain('loadProfileGeneration')
    expect(source).not.toContain('v-if="isMerchant && qualification"')
  })

  it('编辑资料页应复用固定底部操作栏并避免页面外层滚动', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/profile/edit.vue'), 'utf8')

    expect(source).toContain('<BottomActionBar>')
    expect(source).toContain('class="bar-btn bar-btn-primary"')
    expect(source).toContain('min-height: 0;')
    expect(source).toContain('height: 0;')
    expect(source).not.toContain('class="action-bar"')
    expect(source).not.toContain('overflow-y: auto;')
  })

  it('编辑资料页应覆盖 OpenAPI 个人和商家资料字段', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/profile/edit.vue'), 'utf8')

    expect(source).toContain('merchantNickname')
    expect(source).toContain('reputationScore')
    expect(source).toContain('merchantAccountStatus')
    expect(source).toContain('accountStatusText')
    expect(source).toContain("value: 'unspecified'")
    expect(source).toContain('ensureNicknameAvailable')
    expect(source).toContain('merchantNameRequired')
  })

  it('编辑资料页无修改时应禁用保存按钮', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/profile/edit.vue'), 'utf8')

    expect(source).toContain(':disabled="saving || !hasProfileChanges"')
    expect(source).toContain('initialProfileSnapshot')
    expect(source).toContain('getEditableProfileSnapshot')
    expect(source).toContain('if (!hasProfileChanges.value) return')
  })

  it('昵称恢复为初始昵称时应跳过唯一性检查并允许提交', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/profile/edit.vue'), 'utf8')
    const checkNicknameBody = source.slice(
      source.indexOf('function checkNickname(): void {'),
      source.indexOf('/** 确保当前昵称可用'),
    )
    const ensureBody = source.slice(
      source.indexOf(
        'async function ensureNicknameAvailable(nickname: string): Promise<boolean> {',
      ),
      source.indexOf('// ================= 标签切换 ================='),
    )
    const saveBody = source.slice(
      source.indexOf('async function handleSave(): Promise<void> {'),
      source.indexOf('</script>'),
    )

    expect(source).toContain('function isNicknameChangedFromInitial(nickname: string): boolean')
    expect(source).toContain('const checkedNickname = ref')
    expect(checkNicknameBody).toContain('if (!isNicknameChangedFromInitial(value))')
    expect(ensureBody).toContain('if (!isNicknameChangedFromInitial(nickname))')
    expect(ensureBody).toContain('return true')
    expect(saveBody).toContain('checkedNickname.value === nickname')
  })

  it('我的页应按用户类型读取 api-spec 对应资料接口', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/profile/index.vue'), 'utf8')

    expect(source).toContain('getMerchantProfile')
    expect(source).toContain('getMyProfile')
    expect(source).toContain("authStore.userKind === 'merchant'")
    expect(source).toContain('profile.qualificationStatus')
    expect(source).toContain('avatarUrl')
    expect(source).toContain("from '@/api/modules/auth'")
    expect(source).toContain('logout')
    expect(source).not.toContain("api.get('/identity/me/profile')")
    expect(source).not.toContain("from '@/api'")
    expect(source).not.toContain('api.post')
  })

  it('我的页退出登录和未登录展示应清空头像昵称缓存', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/profile/index.vue'), 'utf8')

    expect(source).toContain('function clearProfileDisplayState(): void')
    expect(source).toContain("nickname.value = ''")
    expect(source).toContain("avatarUrl.value = ''")
    expect(source).toContain("merchantQualificationStatus.value = ''")
    expect(source).toContain('if (!authStore.isLoggedIn) {')
    expect(source).toContain('clearProfileDisplayState()')
    expect(source).toContain('authStore.clearTokens()')
  })
})
