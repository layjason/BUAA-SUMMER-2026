/**
 * geo-service 单元测试
 *
 * 测试范围：mockLocationsToPOI（纯函数，不依赖 uni-app API）
 * 其他函数（searchPOI、reverseGeocode、geocode、getCurrentLocation）依赖 uni.request / uni.getLocation，
 * 这些 API 在 vitest/jsdom 环境中不可用，且项目中没有 uni-app mock 工具链，
 * 因此不对它们编写单元测试。
 *
 * SKIP-UNIT-TEST-FOR: searchPOI, reverseGeocode, geocode, getCurrentLocation
 *
 * 这些函数的核心逻辑是调用 uni.request 调用高德 REST API 或 uni.getLocation 获取定位，
 * uni-app API 在 vitest/jsdom 中不可用。如果需要测试，需要在 global 上 mock uni 对象，
 * 目前项目没有这套 mock 基础设施，引入会增加维护成本。
 * 此外，这些函数的异常降级逻辑已通过 fallbackSearchPOI 路径覆盖，
 * 而 fallbackSearchPOI 本身由 MOCK_ACTIVITY_LOCATIONS 预设数据提供回退保障。
 */
import { describe, expect, it } from 'vitest'
import { mockLocationsToPOI } from '@/services/amap'
import { MOCK_ACTIVITY_LOCATIONS } from '@/config/mock-locations'

describe('mockLocationsToPOI', () => {
  it('将预设地点转为 POIItem[]', () => {
    const result = mockLocationsToPOI(MOCK_ACTIVITY_LOCATIONS)
    expect(result).toHaveLength(MOCK_ACTIVITY_LOCATIONS.length)
    expect(result[0]).toHaveProperty('id')
    expect(result[0]).toHaveProperty('name')
    expect(result[0]).toHaveProperty('address')
    expect(result[0]).toHaveProperty('longitude')
    expect(result[0]).toHaveProperty('latitude')
    expect(result[0]).toHaveProperty('city')
  })

  it('每个 POIItem 的 id 格式正确', () => {
    const result = mockLocationsToPOI(MOCK_ACTIVITY_LOCATIONS)
    result.forEach((item, index) => {
      expect(item.id).toBe(`mock_poi_${index}`)
    })
  })

  it('每个 POIItem 的 name 和 address 非空', () => {
    const result = mockLocationsToPOI(MOCK_ACTIVITY_LOCATIONS)
    result.forEach((item) => {
      expect(item.name).toBeTruthy()
      expect(item.address).toBeTruthy()
    })
  })

  it('经度纬度应在合理范围内', () => {
    const result = mockLocationsToPOI(MOCK_ACTIVITY_LOCATIONS)
    result.forEach((item) => {
      expect(item.longitude).toBeGreaterThanOrEqual(73)
      expect(item.longitude).toBeLessThanOrEqual(135)
      expect(item.latitude).toBeGreaterThanOrEqual(18)
      expect(item.latitude).toBeLessThanOrEqual(54)
    })
  })
})
