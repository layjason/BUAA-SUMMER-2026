import { describe, expect, it, vi } from 'vitest'
import {
  buildActivityLocationDisplay,
  buildActivityLocationMapRoute,
  openActivityLocationMap,
} from '@/services/activity-location'

describe('activity-location', () => {
  it('buildActivityLocationDisplay prefers placeName as title', () => {
    const display = buildActivityLocationDisplay({
      point: { longitude: 116.347, latitude: 39.972 },
      city: '北京',
      address: '北京市海淀区学院路',
      placeName: '北航体育馆',
    })

    expect(display.title).toBe('北航体育馆')
    expect(display.address).toBe('北京市海淀区学院路')
    expect(display.city).toBe('北京')
    expect(display.longitude).toBe(116.347)
    expect(display.latitude).toBe(39.972)
  })

  it('buildActivityLocationMapRoute encodes location query', () => {
    const route = buildActivityLocationMapRoute({
      title: '北航体育馆',
      address: '北京市海淀区学院路',
      city: '北京',
      longitude: 116.347,
      latitude: 39.972,
    })

    expect(route).toContain('/pages/activity/location-map?')
    expect(route).toContain('latitude=39.972')
    expect(route).toContain('longitude=116.347')
    expect(route).toContain(encodeURIComponent('北航体育馆'))
  })

  it('openActivityLocationMap navigates and falls back to openLocation', () => {
    const navigateTo = vi.fn((_options: { fail?: () => void }) => {})
    const openLocation = vi.fn()
    vi.stubGlobal('uni', { navigateTo, openLocation })

    const display = {
      title: '北航体育馆',
      address: '北京市海淀区学院路',
      city: '北京',
      longitude: 116.347,
      latitude: 39.972,
    }

    openActivityLocationMap(display)

    expect(navigateTo).toHaveBeenCalledWith(
      expect.objectContaining({
        url: buildActivityLocationMapRoute(display),
      }),
    )

    const call = navigateTo.mock.calls[0]?.[0] as { fail?: () => void }
    call.fail?.()
    expect(openLocation).toHaveBeenCalledWith(
      expect.objectContaining({
        latitude: 39.972,
        longitude: 116.347,
        name: '北航体育馆',
      }),
    )
  })
})
