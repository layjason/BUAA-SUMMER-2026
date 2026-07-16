/**
 * 将本地或网络图片保存到用户设备。
 *
 * 前置条件：filePath 为 uni.downloadFile 临时路径、blob URL 或网络图片地址。
 * 后置条件：移动端尝试写入相册；H5 尝试触发浏览器下载。
 */
export async function saveImageToDevice(filePath: string, filename?: string): Promise<void> {
  if (!filePath) throw new Error('empty file path')

  // #ifdef H5
  const link = document.createElement('a')
  link.href = filePath
  link.download = filename ?? `image-${Date.now()}.jpg`
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  return
  // #endif

  await new Promise<void>((resolve, reject) => {
    // #ifdef APP-PLUS
    const savePath = filePath.startsWith('http')
      ? filePath
      : plus.io.convertLocalFileSystemURL(filePath)
    plus.gallery.save(
      savePath,
      () => resolve(),
      (err) => reject(new Error(`gallery save failed: ${JSON.stringify(err)}`)),
    )
    // #endif

    // #ifndef APP-PLUS
    uni.saveImageToPhotosAlbum({
      filePath,
      success: () => resolve(),
      fail: (err) => reject(new Error(`save image failed: ${JSON.stringify(err)}`)),
    })
    // #endif
  })
}
