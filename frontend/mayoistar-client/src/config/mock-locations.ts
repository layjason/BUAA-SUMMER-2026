/**
 * 活动创建页 mock 地点选项
 *
 * 不接真实地图 SDK，提供可演示的预设地点与坐标。
 */

export interface MockLocationOption {
  placeName: string
  address: string
  city: string
  point: {
    longitude: number
    latitude: number
  }
}

/** 活动编辑页可选 mock 地点 */
export const MOCK_ACTIVITY_LOCATIONS: MockLocationOption[] = [
  {
    placeName: '奥林匹克体育中心',
    address: '朝阳区奥林匹克体育中心',
    city: '北京',
    point: { longitude: 116.397, latitude: 39.992 },
  },
  {
    placeName: '国贸地铁站',
    address: '朝阳区国贸地铁站 A 口',
    city: '北京',
    point: { longitude: 116.461, latitude: 39.909 },
  },
  {
    placeName: '王府井书店',
    address: '东城区王府井书店三层活动区',
    city: '北京',
    point: { longitude: 116.417, latitude: 39.914 },
  },
  {
    placeName: '朝阳公园',
    address: '朝阳区朝阳公园南门',
    city: '北京',
    point: { longitude: 116.483, latitude: 39.933 },
  },
]
