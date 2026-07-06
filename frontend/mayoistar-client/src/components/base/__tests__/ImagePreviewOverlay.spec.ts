import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('ImagePreviewOverlay', () => {
  it('应提供可点击关闭按钮和遮罩关闭', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/components/base/ImagePreviewOverlay.vue'),
      'utf8',
    )

    expect(source).toContain('class="preview-close"')
    expect(source).toContain('@tap.stop="emitClose"')
    expect(source).toContain('class="preview-overlay"')
    expect(source).toContain('z-index: 10000')
    expect(source).toContain('保存图片')
    expect(source).toContain('saveImageToDevice')
  })
})
