/**
 * 错误码映射单元测试
 *
 * 验证 getErrorMessage 返回的提示与 TypeSpec 定义一致
 */
import { describe, it, expect } from 'vitest'
import { getErrorMessage, resolveApiError } from '@/utils/error'
import { BusinessError } from '@/api/types'

describe('getErrorMessage - Identity 错误码', () => {
  it('10001 应返回邮箱已被注册', () => {
    expect(getErrorMessage(10001)).toBe('该邮箱已被注册')
  })

  it('10002 应返回昵称已被占用', () => {
    expect(getErrorMessage(10002)).toBe('昵称已被占用')
  })

  it('10003 应返回邮箱或密码错误', () => {
    expect(getErrorMessage(10003)).toBe('邮箱或密码错误')
  })

  it('10004 应返回账号未激活', () => {
    expect(getErrorMessage(10004)).toBe('账号尚未激活，请先激活')
  })

  it('10005 应返回账号已被封禁', () => {
    expect(getErrorMessage(10005)).toBe('账号已被封禁')
  })

  it('10007 应返回登录已过期', () => {
    expect(getErrorMessage(10007)).toBe('登录已过期，请重新登录')
  })

  it('10011 应返回该邮箱未注册', () => {
    expect(getErrorMessage(10011)).toBe('该邮箱未注册')
  })

  it('10016 应返回当前密码不正确', () => {
    expect(getErrorMessage(10016)).toBe('当前密码不正确')
  })
})

describe('getErrorMessage - Activities 错误码', () => {
  it('20002 应返回活动不存在或已下架', () => {
    expect(getErrorMessage(20002)).toBe('活动不存在或已下架')
  })

  it('20006 应返回活动已关闭报名', () => {
    expect(getErrorMessage(20006)).toBe('活动已关闭报名')
  })

  it('20007 应返回您已报名该活动', () => {
    expect(getErrorMessage(20007)).toBe('您已报名该活动')
  })
})

describe('getErrorMessage - 回退行为', () => {
  it('未知错误码应返回自定义回退消息', () => {
    expect(getErrorMessage(99999, '自定义错误')).toBe('自定义错误')
  })

  it('未知错误码应返回默认回退消息', () => {
    expect(getErrorMessage(99999)).toBe('操作失败，请稍后重试')
  })
})

describe('resolveApiError', () => {
  it('优先使用服务端 message（内部错误码）', () => {
    const err = new BusinessError(90000, '数据库字段缺失')
    expect(resolveApiError(err, '发送失败')).toBe('数据库字段缺失')
  })

  it('已知业务码仍使用映射文案', () => {
    const err = new BusinessError(40006, '好友申请已存在')
    expect(resolveApiError(err, '发送失败')).toBe('好友申请已存在')
  })
})
