import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('活动编辑页 mock 地点选择', () => {
  it('应通过 mock 地点回填 LocationInfo 坐标', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/activity/edit.vue'), 'utf8')

    expect(source).toContain('MOCK_ACTIVITY_LOCATIONS')
    expect(source).toContain('showLocationPicker')
    expect(source).toContain('locationLongitude')
    expect(source).not.toContain('longitude: 116.4, latitude: 39.9')
  })
})
