/**
 * 聊天位置消息发送
 *
 * 负责将位置消息载荷转为 OpenAPI 结构并调用聊天 API，不直接操作地图服务。
 *
 * 前置条件：conversationId 有效且用户有会话发送权限
 * 后置条件：返回服务端确认的 ChatMessage
 * 不变量：发送失败时向外抛出异常，由页面层处理 toast
 */
import { sendMessage } from '@/api/modules/chat'
import type { components } from '@/api/types/schema'
import type { POIItem } from '@/services/amap'
import {
  createCurrentLocationMessage,
  createLocationMessage,
  toApiLocationInfo,
} from '@/services/location-message'

type LocationInfo = components['schemas']['LocationInfo']
type ChatMessage = components['schemas']['Chat.ChatMessage']

/**
 * 获取当前位置对应的 OpenAPI LocationInfo（发送前组装用）。
 *
 * 前置条件：无（定位失败时 amap 服务会返回默认坐标）
 * 后置条件：始终返回符合 OpenAPI LocationInfo 的结构
 */
export async function pickChatLocation(): Promise<LocationInfo> {
  const payload = await createCurrentLocationMessage()
  return toApiLocationInfo(payload)
}

/**
 * 发送当前位置消息。
 *
 * @param conversationId 会话 ID
 */
export async function sendCurrentLocationMessage(conversationId: string): Promise<ChatMessage> {
  const location = await pickChatLocation()
  return sendMessage(conversationId, { kind: 'location', location })
}

/**
 * 发送指定 POI 的位置消息。
 *
 * @param conversationId 会话 ID
 * @param poi 高德 POI 条目
 */
export async function sendPoiLocationMessage(
  conversationId: string,
  poi: POIItem,
): Promise<ChatMessage> {
  const payload = await createLocationMessage({
    longitude: poi.longitude,
    latitude: poi.latitude,
    title: poi.name,
    address: poi.address,
    city: poi.city,
    nearbyPOI: poi,
  })
  return sendMessage(conversationId, {
    kind: 'location',
    location: toApiLocationInfo(payload),
  })
}
