import { describe, expect, it } from 'vitest'
import { readFirstQueryString } from '@/utils/page-query'

describe('page-query', () => {
  it('应按优先级读取第一个非空 query 字符串', () => {
    expect(
      readFirstQueryString({ id: 'fallback', activityId: 'primary' }, ['activityId', 'id']),
    ).toBe('primary')
  })

  it('应支持活动详情旧入口 id 参数并去除空白', () => {
    expect(readFirstQueryString({ id: '  activity-1  ' }, ['activityId', 'id'])).toBe('activity-1')
  })

  it('应解码 URL 编码参数', () => {
    expect(readFirstQueryString({ activityId: 'draft%2F1' }, ['activityId'])).toBe('draft/1')
  })

  it('缺少参数时应返回空字符串', () => {
    expect(readFirstQueryString(undefined, ['activityId', 'id'])).toBe('')
    expect(readFirstQueryString({ activityId: '   ' }, ['activityId', 'id'])).toBe('')
  })
})
