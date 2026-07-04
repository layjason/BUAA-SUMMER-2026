import { describe, expect, it } from 'vitest'
import { createMapMoveRequest, isValidMapCoordinate } from '@/utils/map-move'

describe('map-move', () => {
  it('接受合法经纬度', () => {
    expect(isValidMapCoordinate(39.908, 116.397)).toBe(true)
    expect(createMapMoveRequest(39.908, 116.397)).toEqual({
      latitude: 39.908,
      longitude: 116.397,
    })
  })

  it('拒绝超出范围的经纬度', () => {
    expect(isValidMapCoordinate(91, 116.397)).toBe(false)
    expect(isValidMapCoordinate(39.908, 181)).toBe(false)
    expect(createMapMoveRequest(91, 116.397)).toBeNull()
  })

  it('拒绝非有限数坐标', () => {
    expect(isValidMapCoordinate(Number.NaN, 116.397)).toBe(false)
    expect(isValidMapCoordinate(39.908, Number.POSITIVE_INFINITY)).toBe(false)
    expect(createMapMoveRequest(Number.NaN, 116.397)).toBeNull()
  })
})
