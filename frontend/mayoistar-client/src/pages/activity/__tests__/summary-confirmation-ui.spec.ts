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
    expect(source).toContain('const markdownUrl = r.signedUrl ? toAbsoluteMediaUrl(r.signedUrl) :')
    expect(source).toContain('imageUrls.value.push(previewUrl)')
    expect(source).toContain('reviewMarkdownImageUrls.value.push(markdownUrl)')
    expect(source).toContain('content.value += `![评价图片](${markdownUrl})`')
    expect(source).toContain('const removedUrl = reviewMarkdownImageUrls.value[index]')
    expect(source).toContain('function returnToActivityDetail(): void')
    expect(source).toContain('getCurrentPages() as PageRouteSnapshot[]')
    expect(source).toContain("previousPage?.route === 'pages/activity/detail'")
    expect(source).toContain('previousPage.options?.activityId === activityId.value')
    expect(source).toContain('uni.navigateBack()')
    expect(source).toContain(
      'uni.redirectTo({ url: `/pages/activity/detail?activityId=${activityId.value}` })',
    )
    expect(source).toContain('setTimeout(returnToActivityDetail, 1500)')
    expect(source).toContain('setTimeout(returnToActivityDetail, 1000)')
    expect(source).not.toContain('imageUrls.value.push(imageUrl)')
  })

  it('活动编辑新上传图片应本地预览，草稿回显应下载私有 signedUrl 后预览', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/activity/edit.vue'), 'utf8')
    const viteConfigSource = readFileSync(resolve(process.cwd(), 'vite.config.ts'), 'utf8')
    const mediaPreviewSource = readFileSync(
      resolve(process.cwd(), 'src/utils/media-preview.ts'),
      'utf8',
    )

    expect(source).toContain('imagePreviews.value.push(tempPath)')
    expect(source).toContain('resolveMediaPreviewUrl')
    expect(mediaPreviewSource).toContain('header: { Authorization: `Bearer ${accessToken}` }')
    expect(source).toContain('imagePreviews.value = await Promise.all(')
    expect(viteConfigSource).toContain("'/media': {")
    expect(source).not.toContain('imagePreviews.value.push(result.signedUrl || tempPath)')
  })

  it('活动详情及总结评价详情应解析媒体签名 URL 后再展示', () => {
    const detailSource = readFileSync(
      resolve(process.cwd(), 'src/pages/activity/detail.vue'),
      'utf8',
    )
    const summaryDetailSource = readFileSync(
      resolve(process.cwd(), 'src/pages/activity/summary-detail.vue'),
      'utf8',
    )
    const reviewDetailSource = readFileSync(
      resolve(process.cwd(), 'src/pages/activity/review-detail.vue'),
      'utf8',
    )

    expect(detailSource).toContain('activityImagePreviews')
    expect(detailSource).toContain('loadActivityImagePreviews')
    expect(detailSource).toContain('resolveMediaPreviewUrl(image.signedUrl')
    expect(detailSource).not.toContain(':src="img.signedUrl"')
    expect(detailSource).not.toContain(':src="activity.images[0].signedUrl"')
    expect(summaryDetailSource).toContain('summaryImagePreviews')
    expect(summaryDetailSource).toContain('resolveMediaPreviewUrl(image.signedUrl')
    expect(reviewDetailSource).toContain('loadReviewImagePreviews')
    expect(reviewDetailSource).toContain('resolveMediaPreviewUrl(url, accessToken)')
  })
})
