import { describe, expect, it } from 'vitest';
import { getAdminErrorMessage, resolveUnauthorizedMessage } from './adminErrorMessages';

describe('adminErrorMessages', () => {
  it('映射后台业务错误码为中文', () => {
    expect(getAdminErrorMessage(60006)).toBe('请填写审核、下架或停用原因');
    expect(getAdminErrorMessage(60009)).toBe('活动当前状态不允许该操作');
  });

  it('未知错误码回退到服务端 message', () => {
    expect(getAdminErrorMessage(99999, 'Server says no')).toBe('Server says no');
  });

  it('401 英文提示统一为中文', () => {
    expect(resolveUnauthorizedMessage('Authentication is required')).toBe('登录已过期，请重新登录');
    expect(resolveUnauthorizedMessage()).toBe('登录已过期，请重新登录');
    expect(resolveUnauthorizedMessage('自定义提示')).toBe('自定义提示');
  });
});
