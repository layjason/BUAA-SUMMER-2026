import { getMerchantProfile, getMyProfile } from '@/api/modules/profile'
import { useAuthStore } from '@/stores/auth'
import { resolveAvatarDisplayUrl } from '@/utils/avatar-display'

export interface CurrentUserProfileDisplay {
  nickname: string
  avatarUrl: string
  initialChar: string
}

/** 加载当前登录用户在资料页与消息页统一展示的头像和昵称 */
export async function loadCurrentUserProfileDisplay(): Promise<CurrentUserProfileDisplay> {
  const authStore = useAuthStore()
  if (!authStore.isLoggedIn) {
    return { nickname: '', avatarUrl: '', initialChar: '?' }
  }

  const fallbackInitial = (authStore.userId ?? '?').charAt(0).toUpperCase()

  try {
    const accessToken = authStore.getAccessToken()

    if (authStore.userKind === 'merchant') {
      const profile = await getMerchantProfile()
      const nickname = profile.nickname ?? ''
      return {
        nickname,
        avatarUrl: await resolveAvatarDisplayUrl(profile.avatar?.signedUrl, accessToken),
        initialChar: nickname.charAt(0).toUpperCase() || fallbackInitial,
      }
    }

    const profile = await getMyProfile()
    const nickname = profile.nickname ?? ''
    return {
      nickname,
      avatarUrl: await resolveAvatarDisplayUrl(profile.avatar?.signedUrl, accessToken),
      initialChar: nickname.charAt(0).toUpperCase() || fallbackInitial,
    }
  } catch {
    return { nickname: '', avatarUrl: '', initialChar: fallbackInitial }
  }
}
