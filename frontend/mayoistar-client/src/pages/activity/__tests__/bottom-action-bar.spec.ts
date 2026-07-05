import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

function readActivityPage(filename: string): string {
  return readFileSync(resolve(process.cwd(), 'src/pages/activity', filename), 'utf8')
}

describe('活动页面底部操作栏', () => {
  it('编辑页应显式导入 BottomActionBar 以确保样式组件被解析', () => {
    const source = readActivityPage('edit.vue')

    expect(source).toContain('<BottomActionBar>')
    expect(source).toMatch(/import\s+\{[^}]*BottomActionBar[^}]*}\s+from\s+['"]@\/components['"]/)
  })

  it('模板选择页应使用固定底部操作栏，避免按钮被手机安全区遮挡', () => {
    const source = readActivityPage('templates.vue')

    expect(source).toContain('<BottomActionBar>')
    expect(source).toMatch(/import\s+\{[^}]*BottomActionBar[^}]*}\s+from\s+['"]@\/components['"]/)
    expect(source).not.toContain('class="action-bar"')
  })

  it('详情页主操作按钮应复用固定底部操作栏样式', () => {
    const source = readActivityPage('detail.vue')

    expect(source).toContain('<BottomActionBar>')
    expect(source).toMatch(/import\s+\{[^}]*BottomActionBar[^}]*}\s+from\s+['"]@\/components['"]/)
    expect(source).toContain('class="bar-btn bar-btn-primary"')
    expect(source).not.toContain('class="action-bar"')
    expect(source).not.toContain('class="action-btn"')
  })

  it('详情页主按钮应让报名中未来活动优先取消报名而不是签到', () => {
    const source = readActivityPage('detail.vue')
    const buttonTextBody = source.slice(
      source.indexOf('const buttonText = computed(() => {'),
      source.indexOf('const buttonDisabled = computed(() => {'),
    )
    const handleActionBody = source.slice(
      source.indexOf('function handleAction(): void {'),
      source.indexOf('/**\n * 取消报名确认弹窗'),
    )

    expect(buttonTextBody.indexOf("p.status === 'registered'")).toBeLessThan(
      buttonTextBody.indexOf('p.canCheckIn'),
    )
    expect(handleActionBody.indexOf("p.status === 'registered'")).toBeLessThan(
      handleActionBody.indexOf('p.canCheckIn'),
    )
  })
})
