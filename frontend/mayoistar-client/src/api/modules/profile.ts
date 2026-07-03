/**
 * 用户资料 API 模块
 *
 * 封装个人资料查看与编辑、商户资料、头像上传、昵称检查等接口。
 */
import { get, patch, post, upload } from '@/api/request'
import type { components } from '@/api/types/schema'

type MediaFile = components['schemas']['MediaFile']
type UpdatePersonalProfileRequest = components['schemas']['Identity.UpdatePersonalProfileRequest']
type UpdateMerchantProfileRequest = components['schemas']['Identity.UpdateMerchantProfileRequest']
type QualificationSubmitRequest = components['schemas']['Identity.QualificationSubmitRequest']

/** 获取当前用户个人资料 */
export function getMyProfile() {
  return get('/identity/me/profile')
}

/** 更新当前用户个人资料 */
export function updateMyProfile(data: UpdatePersonalProfileRequest) {
  return patch('/identity/me/profile', {
    body: data,
  })
}

/** 获取当前商户资料 */
export function getMerchantProfile() {
  return get('/identity/me/merchant-profile')
}

/** 更新当前商户资料 */
export function updateMerchantProfile(data: UpdateMerchantProfileRequest) {
  return patch('/identity/me/merchant-profile', {
    body: data,
  })
}

/** 获取兴趣标签列表 */
export function getInterestTags() {
  return get('/identity/interest-tags')
}

/** 检查昵称是否可用 */
export function checkNicknameAvailability(nickname: string) {
  return get('/identity/nicknames/availability', {
    query: { nickname },
  })
}

/** 上传用户头像 */
export function uploadAvatar(filePath: string): Promise<MediaFile> {
  return upload<MediaFile>('/identity/media/avatar', filePath)
}

/** 提交商户资质（营业执照） */
export function submitMerchantQualification(licenseMediaIds: string[]) {
  const body: QualificationSubmitRequest = { licenseMediaIds }
  return post('/identity/me/merchant-qualification', {
    body,
  })
}
