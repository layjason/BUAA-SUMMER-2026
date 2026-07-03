/**
 * M0 工程基础冒烟测试
 *
 * 验证 vitest 基础配置正确、类型可用
 */
import { describe, it, expect } from 'vitest'
import { BusinessError, TokenExpiredError } from '@/api/types'
import { formatDateTime } from '@/utils/date'
import { getErrorMessage } from '@/utils/error'

describe('BusinessError', () => {
  it('应正确创建业务错误', () => {
    const err = new BusinessError(10000, 'Email already registered')
    expect(err.code).toBe(10000)
    expect(err.message).toBe('Email already registered')
    expect(err.name).toBe('BusinessError')
  })
})

describe('TokenExpiredError', () => {
  it('应正确创建 Token 过期错误', () => {
    const err = new TokenExpiredError()
    expect(err.name).toBe('TokenExpiredError')
  })
})

describe('formatDateTime', () => {
  it('应正确格式化 ISO 日期时间', () => {
    const result = formatDateTime('2023-10-05T14:30:00Z')
    expect(result).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/)
  })
})

describe('getErrorMessage', () => {
  it('应返回已知错误码的中文提示', () => {
    expect(getErrorMessage(10001)).toBe('该邮箱已被注册')
  })

  it('应为未知错误码返回回退消息', () => {
    expect(getErrorMessage(99999, '未知错误')).toBe('未知错误')
  })
})
