import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('活动编辑页地图选点', () => {
  it('应包含地图选点交互要素', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/activity/edit.vue'), 'utf8')

    expect(source).toContain('goToMapPicker')
    expect(source).toContain('hasLocation')
    expect(source).toContain('markers')
    expect(source).toContain('locationLongitude')
    expect(source).not.toContain('longitude: 116.4, latitude: 39.9')
  })
  it('地图选点页应避免将输入和确认控件覆盖在原生地图层内', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/activity/map-picker.vue'), 'utf8')
    const mapAreaStart = source.indexOf('<view class="mp-map-area">')
    const mapAreaEnd = source.indexOf('<view class="mp-bottom-area">')
    const mapAreaTemplate = source.slice(mapAreaStart, mapAreaEnd)

    expect(mapAreaTemplate).toContain('<cover-image class="mp-center-pin"')
    expect(mapAreaTemplate).not.toContain('class="mp-search-bar"')
    expect(mapAreaTemplate).not.toContain('class="mp-confirm-btn"')
  })
})
