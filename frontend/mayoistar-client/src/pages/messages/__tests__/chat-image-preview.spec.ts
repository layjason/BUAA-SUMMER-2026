import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('聊天图片预览', () => {
  it('图片消息应脱离绿色气泡并支持全屏保存预览', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/messages/chat.vue'), 'utf8')

    expect(source).toContain('message-image-wrap')
    expect(source).toContain('onImageMessageTap')
    expect(source).toContain('ImagePreviewOverlay')
    expect(source).toContain(':show-save="true"')
    expect(source).not.toContain('v-else-if="msg.kind === \'image\' && getImageUrl(msg)"')
    expect(source).toContain('flex-shrink: 0')
    expect(source).toContain('UserAvatar')
    expect(source).toContain('message-image-wrap')
  })

  it('小队资料未加载时不应闪现只读停用提示', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/messages/chat.vue'), 'utf8')

    expect(source).toContain('showReadonlyHint')
    expect(source).toContain('teamContextReady')
    expect(source).not.toMatch(/v-if="conversationKind === 'team' && !teamWritable"/)
  })
})
