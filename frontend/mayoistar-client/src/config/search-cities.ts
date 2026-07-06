/**
 * 活动搜索页可选城市（按城市等级分组）
 */

export interface SearchCityGroup {
  label: string
  cities: readonly string[]
}

/** 搜索页城市筛选分组 */
export const SEARCH_CITY_GROUPS: readonly SearchCityGroup[] = [
  {
    label: '一线城市',
    cities: ['上海', '北京', '深圳', '广州'],
  },
  {
    label: '新一线城市',
    cities: [
      '成都',
      '杭州',
      '重庆',
      '苏州',
      '武汉',
      '西安',
      '南京',
      '长沙',
      '天津',
      '郑州',
      '东莞',
      '无锡',
      '宁波',
      '青岛',
      '合肥',
    ],
  },
  {
    label: '二线城市',
    cities: [
      '佛山',
      '沈阳',
      '昆明',
      '济南',
      '厦门',
      '福州',
      '温州',
      '常州',
      '大连',
      '石家庄',
      '南宁',
      '哈尔滨',
      '金华',
      '南昌',
      '长春',
      '南通',
      '泉州',
      '贵阳',
      '嘉兴',
      '太原',
      '惠州',
      '徐州',
      '绍兴',
      '中山',
      '台州',
      '烟台',
      '珠海',
      '保定',
      '潍坊',
      '兰州',
    ],
  },
] as const

/** 全部可选城市（去重保序） */
export const ALL_SEARCH_CITIES: string[] = [
  ...new Set(SEARCH_CITY_GROUPS.flatMap((group) => group.cities)),
]
