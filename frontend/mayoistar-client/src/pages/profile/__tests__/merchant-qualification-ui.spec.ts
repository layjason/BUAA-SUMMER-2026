import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('商家资料资质提交 UI', () => {
  it('应通过 API modules 提交营业凭证并展示审核状态', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/profile/edit.vue'), 'utf8')

    expect(source).toContain('submitMerchantQualification')
    expect(source).toContain('uploadMerchantLicense')
    expect(source).toContain('chooseLicenseImages')
    expect(source).toContain('canSubmitQualification')
    expect(source).toContain('licenseMediaIds')
    expect(source).toContain('qualificationStatusText')
    expect(source).toContain('profile.qualification?.status ?? profile.qualificationStatus')
    expect(source).not.toContain('v-if="isMerchant && qualification"')
  })
})
