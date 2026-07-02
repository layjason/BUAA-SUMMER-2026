import { describe, expect, it } from 'vitest'
import { isActivityAtCapacity } from '@/utils/activity-capacity'

describe('isActivityAtCapacity', () => {
  it('未满员时应返回 false', () => {
    expect(isActivityAtCapacity(6, 7)).toBe(false)
  })

  it('已占用名额达到上限时应返回 true', () => {
    expect(isActivityAtCapacity(7, 7)).toBe(true)
  })

  it('已占用名额超过上限时应返回 true', () => {
    expect(isActivityAtCapacity(8, 7)).toBe(true)
  })
})
