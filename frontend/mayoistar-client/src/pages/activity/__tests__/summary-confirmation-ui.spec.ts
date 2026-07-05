import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('活动总结 AI 分类人工确认 UI', () => {
  it('应在提交前允许人工确认或取消图片标签', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/activity/summary.vue'), 'utf8')

    expect(source).toContain('confirmedImageTagMap')
    expect(source).toContain('toggleConfirmedImageTag')
    expect(source).toContain('isImageTagConfirmed')
    expect(source).toContain('confirmedImageTags')
    expect(source).toContain('confirmedEmptyImageIds')
    expect(source).toContain('confirmImageAsEmpty')
    expect(source).toContain('isImageTagSelectionConfirmed')
  })

  it('总结图片上传后应使用本地路径预览并使用 mediaId 提交', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/activity/summary.vue'), 'utf8')

    expect(source).toContain('const selectedPaths = res.tempFilePaths as string[]')
    expect(source).toContain('const previewUrl = selectedPaths[index]')
    expect(source).toContain('imagePreviews.value.push(previewUrl)')
    expect(source).toContain('imageIds.value.push(mediaId)')
    expect(source).not.toContain('imagePreviews.value.push(url ||')
  })

  it('评价图片应本地预览并按文档插入 signedUrl Markdown', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/activity/review.vue'), 'utf8')

    expect(source).toContain('reviewMarkdownImageUrls')
    expect(source).toContain('const selectedPaths = res.tempFilePaths as string[]')
    expect(source).toContain('const previewUrl = selectedPaths[index]')
    expect(source).toContain('const markdownUrl = r.signedUrl ||')
    expect(source).toContain('imageUrls.value.push(previewUrl)')
    expect(source).toContain('reviewMarkdownImageUrls.value.push(markdownUrl)')
    expect(source).toContain('content.value += `![评价图片](${markdownUrl})`')
    expect(source).toContain('const removedUrl = reviewMarkdownImageUrls.value[index]')
    expect(source).toContain('function returnToActivityDetail(): void')
    expect(source).toContain(
      'uni.redirectTo({ url: `/pages/activity/detail?activityId=${activityId.value}` })',
    )
    expect(source).toContain('setTimeout(returnToActivityDetail, 1500)')
    expect(source).toContain('setTimeout(returnToActivityDetail, 1000)')
    expect(source).not.toContain('imageUrls.value.push(imageUrl)')
  })

  it('活动编辑新上传图片应本地预览，草稿回显继续使用 signedUrl', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/activity/edit.vue'), 'utf8')

    expect(source).toContain('imagePreviews.value.push(tempPath)')
    expect(source).toContain('draft.images ?? []).map((i) => i.signedUrl ??')
    expect(source).not.toContain('imagePreviews.value.push(result.signedUrl || tempPath)')
  })
})
