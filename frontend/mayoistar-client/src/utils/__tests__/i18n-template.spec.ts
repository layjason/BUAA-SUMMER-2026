import { describe, expect, it } from 'vitest'
import { formatI18nTemplate } from '../i18n-template'

describe('formatI18nTemplate', () => {
  it('替换命名占位符为实际值', () => {
    expect(formatI18nTemplate('重发激活邮件（{seconds}秒）', { seconds: 58 })).toBe(
      '重发激活邮件（58秒）',
    )
  })

  it('保留缺少值的占位符', () => {
    expect(formatI18nTemplate('{action}（{seconds}秒）', { seconds: 12 })).toBe('{action}（12秒）')
  })
})
