import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('小队媒体预览', () => {
  it('小队相册应通过鉴权下载展示 teamMember 私有图片', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/teams/album.vue'), 'utf8')

    expect(source).toContain('resolveMediaPreviewUrlMap')
    expect(source).toContain('imagePreviewUrls')
    expect(source).toContain('getImagePreviewUrl')
    expect(source).toContain('ImagePreviewOverlay')
    expect(source).toContain('closeImagePreview')
    expect(source).toContain(':show-save="true"')
    expect(source).toContain('inSelectionMode')
    expect(source).toContain('onSaveSelected')
    expect(source).toContain('保存 (${selectedIds.length})')
    expect(source).toContain('if (inSelectionMode.value)')
    expect(source).toContain('openImagePreview(image)')
    expect(source).not.toContain('uni.previewImage')
    expect(source).not.toContain(':src="image.signedUrl"')
  })

  it('群文件应为图片生成鉴权预览并用于缩略图和预览', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/teams/files.vue'), 'utf8')

    expect(source).toContain('resolveMediaPreviewUrlMap')
    expect(source).toContain('resolveMediaPreviewUrl')
    expect(source).toContain('getFilePreviewUrl')
    expect(source).toContain('class="file-thumb"')
    expect(source).toContain('ImagePreviewOverlay')
    expect(source).not.toContain('uni.previewImage')
  })
})
