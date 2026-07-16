/**
 * 个人二维码：展示与扫码加好友
 *
 * 真实环境走 GET /social/qr-code（PNG）与 POST /social/qr-code/scan。
 * 开发 fallback：手动输入 token 或 userId。
 */
import { getBinary } from '@/api/request'
import { scanPersonalQrCode } from '@/api/modules/social'
/** ArrayBuffer → data URL（用于 image 组件展示 PNG） */
export function arrayBufferToDataUrl(buffer: ArrayBuffer, mimeType: string): string {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  const base64 = btoa(binary)
  return `data:${mimeType};base64,${base64}`
}

/** 拉取当前用户个人二维码图片 data URL */
export async function fetchPersonalQrCodeDataUrl(): Promise<string> {
  const buffer = await getBinary('/social/qr-code')
  return arrayBufferToDataUrl(buffer, 'image/png')
}

/** 解析扫码结果并发送 source=qrCode 好友申请 */
export async function submitPersonalQrScan(token: string, message?: string): Promise<void> {
  const trimmed = token.trim()
  if (!trimmed) {
    throw new Error('二维码内容为空')
  }
  await scanPersonalQrCode(trimmed, message)
}

/** 打开扫码加好友页面（H5 展示相机 UI，原生调起扫码） */
export function openPersonalQrScanner(): void {
  uni.navigateTo({ url: '/pages/social/scan-qr' })
}

export function promptManualQrInput(): Promise<string | null> {
  return new Promise((resolve) => {
    uni.showModal({
      title: '手动输入',
      editable: true,
      placeholderText: '粘贴扫码 token 或输入用户 ID',
      success: (res) => {
        if (res.confirm && res.content?.trim()) {
          resolve(res.content.trim())
        } else {
          resolve(null)
        }
      },
      fail: () => resolve(null),
    })
  })
}

/**
 * 跳转扫码页加好友（H5 展示相机 UI，App/小程序在扫码页内调起 uni.scanCode）。
 */
export async function scanPersonalQrAndAddFriend(_message?: string): Promise<boolean> {
  openPersonalQrScanner()
  return true
}
