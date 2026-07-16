import { describe, it, expect, vi } from 'vitest'
import {
  buildLocationDisplay,
  createAmapStaticMapUrl,
  createAmapMarkerUrl,
  buildLocationMapRoute,
  openLocationMap,
  toApiLocationInfo,
} from '@/services/location-message'

describe('location-message', () => {
  it('createAmapStaticMapUrl encodes coordinates', () => {
    const url = createAmapStaticMapUrl(116.31, 39.99)
    expect(url).toContain('restapi.amap.com/v3/staticmap')
    expect(url).toContain('location=116.31%2C39.99')
  })

  it('createAmapMarkerUrl encodes title', () => {
    const url = createAmapMarkerUrl(116.31, 39.99, '测试地点')
    expect(url).toContain('uri.amap.com/marker')
    expect(url).toContain('position=116.31%2C39.99')
    expect(url).toContain('name')
  })

  it('buildLocationDisplay prefers placeName as title', () => {
    const display = buildLocationDisplay({
      point: { longitude: 116.4, latitude: 39.9 },
      city: '北京',
      address: '海淀区知春路',
      placeName: 'Quantum Core Seat',
    })

    expect(display.title).toBe('Quantum Core Seat')
    expect(display.address).toBe('海淀区知春路')
    expect(display.previewImageUrl).toContain('staticmap')
    expect(display.provider).toBe('amap')
    expect(display.type).toBe('location')
  })

  it('buildLocationMapRoute encodes query for location-map page', () => {
    const route = buildLocationMapRoute({
      type: 'location',
      provider: 'amap',
      title: '中山公园南门售票处',
      address: '北京市东城区',
      city: '北京',
      longitude: 116.391,
      latitude: 39.906,
      previewImageUrl: 'https://example.com/map.png',
      openMapUrl: 'https://uri.amap.com/marker?position=116.391,39.906&name=test',
    })

    expect(route).toContain('/pages/messages/location-map?')
    expect(route).toContain('latitude=39.906')
    expect(route).toContain('longitude=116.391')
    expect(route).toContain(encodeURIComponent('中山公园南门售票处'))
  })

  it('openLocationMap navigates to location-map and falls back to uni.openLocation', () => {
    const navigateTo = vi.fn((_opts: { fail?: () => void }) => {})
    const openLocation = vi.fn()
    vi.stubGlobal('uni', { navigateTo, openLocation })

    const payload = {
      type: 'location' as const,
      provider: 'amap' as const,
      title: '中山公园南门售票处',
      address: '北京市东城区',
      city: '北京',
      longitude: 116.391,
      latitude: 39.906,
      previewImageUrl: 'https://example.com/map.png',
      openMapUrl: 'https://uri.amap.com/marker?position=116.391,39.906&name=test',
    }

    openLocationMap(payload)

    expect(navigateTo).toHaveBeenCalledWith(
      expect.objectContaining({
        url: buildLocationMapRoute(payload),
      }),
    )

    const call = navigateTo.mock.calls[0]?.[0] as { fail?: () => void }
    call.fail?.()
    expect(openLocation).toHaveBeenCalledWith(
      expect.objectContaining({
        latitude: 39.906,
        longitude: 116.391,
        name: '中山公园南门售票处',
      }),
    )
  })

  it('toApiLocationInfo maps payload to OpenAPI shape', () => {
    const api = toApiLocationInfo({
      type: 'location',
      provider: 'amap',
      title: '测试地点',
      address: '北京市海淀区',
      city: '北京',
      longitude: 116.4,
      latitude: 39.9,
      previewImageUrl: 'https://example.com/map.png',
      openMapUrl: 'https://example.com/open',
    })

    expect(api.placeName).toBe('测试地点')
    expect(api.point.longitude).toBe(116.4)
    expect(api.point.latitude).toBe(39.9)
  })
})
