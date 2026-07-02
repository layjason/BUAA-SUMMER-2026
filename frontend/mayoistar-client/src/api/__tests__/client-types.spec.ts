import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'

/**
 * 校验 api/modules 不得使用 as never 绕过 OpenAPI 类型检查。
 */
describe('API modules 类型安全', () => {
  it('api/modules 中不应存在 as never', () => {
    const modulesDir = join(process.cwd(), 'src/api/modules')
    const files = readdirSync(modulesDir).filter((name) => name.endsWith('.ts'))

    const offenders: string[] = []
    for (const file of files) {
      const content = readFileSync(join(modulesDir, file), 'utf8')
      if (content.includes('as never')) {
        offenders.push(file)
      }
    }

    expect(offenders).toEqual([])
  })
})
