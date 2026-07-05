import { describe, it, expect, vi, beforeEach } from 'vitest'
import { pickChatLocation } from '@/services/chat-send'

vi.mock('@/services/location-message', () => ({
  createCurrentLocationMessage: vi.fn(async () => ({
    type: 'location',
    provider: 'amap',
    title: '中山公园南门售票处',
    address: '北京市东城区',
    city: '北京',
    longitude: 116.391,
    latitude: 39.906,
    previewImageUrl: 'https://example.com/map.png',
    openMapUrl: 'https://example.com/open',
  })),
  toApiLocationInfo: vi.fn(
    (payload: {
      title: string
      address: string
      city: string
      longitude: number
      latitude: number
    }) => ({
      point: { longitude: payload.longitude, latitude: payload.latitude },
      city: payload.city,
      address: payload.address,
      placeName: payload.title,
    }),
  ),
}))

describe('chat-send', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('pickChatLocation returns OpenAPI LocationInfo from location message payload', async () => {
    const location = await pickChatLocation()

    expect(location.placeName).toBe('中山公园南门售票处')
    expect(location.point.longitude).toBe(116.391)
    expect(location.point.latitude).toBe(39.906)
  })
})
